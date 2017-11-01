/*
 * Copyright 2017 a2tirb
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package visualsearch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import visualsearch.image.ProcessImage;
import visualsearch.image.ProcessedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyExtractor;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
public class IndexImageHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ImageRetrieveService imageRetrieveService;
    private ElasticService elasticService;

    final BodyExtractor<Mono<IndexImageRequest>, ReactiveHttpInputMessage> requestExtractor = BodyExtractors.toMono(IndexImageHandler.IndexImageRequest.class);

    public IndexImageHandler(ImageRetrieveService imageRetrieveService, ElasticService elasticService) {
        this.imageRetrieveService = imageRetrieveService;
        this.elasticService = elasticService;
    }

    public Mono<ServerResponse> fetchAndIndex(ServerRequest request) {
        Mono<IndexImageRequest> indexImageRequestMono = request.body(requestExtractor);
        Mono<ResponsePublisher> responsePublisherMono = computeResponse(indexImageRequestMono);
        return responsePublisherMono.flatMap(responsePublisher -> ServerResponse
                .status(responsePublisher.status)
                .contentType(APPLICATION_JSON)
                .body(responsePublisher.resultMono, responsePublisher.responseClass));


    }

    Mono<ResponsePublisher> computeResponse(Mono<IndexImageRequest> indexImageRequestMono) {
        ProcessedImage.Builder resultBuilder = ProcessedImage.builder();
        return indexImageRequestMono.flatMap(indexImageRequest -> {
                    if (indexImageRequest.imageUrl == null) {
                        return Mono.just(new ResponsePublisher<>(Mono.just(new ErrorMessage("imageUrl was not specified in request")),
                                ErrorMessage.class,
                                HttpStatus.BAD_REQUEST));
                    } else {
                        resultBuilder.imageUrl(indexImageRequest.imageUrl);
                        try {
                            logger.debug("Processing visualsearch.image " + indexImageRequest.imageUrl);
                            return imageRetrieveService.getImage(indexImageRequest)
                                    .flatMap(clientResponse -> {
                                        if (clientResponse.statusCode() != HttpStatus.OK) {
                                            return Mono.just(new ResponsePublisher<>(Mono.just(new ErrorMessage("fetching visualsearch.image returned error")),
                                                    ErrorMessage.class,
                                                    clientResponse.statusCode()));
                                        } else {
                                            logger.debug("got response for visualsearch.image " + indexImageRequest.imageUrl);
                                            return processAndStoreImage(clientResponse.body(), resultBuilder);

                                        }
                                    });
                        } catch (Exception e) {
                            return Mono.just(new ResponsePublisher<>(Mono.just(new ErrorMessage("fetching visualsearch.image failed: " + e.getMessage())),
                                    ErrorMessage.class,
                                    HttpStatus.INTERNAL_SERVER_ERROR));
                        }
                    }
                }
        );
    }


    private Mono<ResponsePublisher> processAndStoreImage(ByteBuffer byteBuffer, ProcessedImage.Builder builder) {
        logger.debug("processing bytes for visualsearch.image " + builder.build().imageUrl);
        ProcessedImage processedImage = ProcessImage.getProcessingResult(byteBuffer, builder);
        logger.debug("processed bytes for visualsearch.image " + builder.build().imageUrl);
        return storeResultInElasticsearch(processedImage);
    }

    private Mono<ResponsePublisher> storeResultInElasticsearch(ProcessedImage processedImage) {
        ObjectMapper toJson = new ObjectMapper();
        String documentBody;
        try {
            documentBody = toJson.writeValueAsString(processedImage);
        } catch (JsonProcessingException e) {
            return Mono.just(new ResponsePublisher<>(Mono.just(new ErrorMessage("Unable to write visualsearch.image processing result to elastic")),
                    ErrorMessage.class,
                    HttpStatus.INTERNAL_SERVER_ERROR));
        }
        return elasticService.post(documentBody).map(
                elasticResponse -> {
                    logger.debug("stored visualsearch.image " + processedImage.imageUrl);
                    if (elasticResponse.getHttpStatus() != HttpStatus.CREATED) {
                        return new ResponsePublisher<>(Mono.just(new ErrorMessage("Unable to write visualsearch.image processing result to elastic")),
                                ErrorMessage.class,
                                elasticResponse.getHttpStatus());
                    }
                    // get the id from request
                    try {
                        HashMap<String, Object> result =
                                new ObjectMapper().readValue(elasticResponse.getBody(), HashMap.class);
                        String id = (String) result.get("_id");
                        IndexImageResponse response = new IndexImageResponse();
                        response._id = id;
                        return new ResponsePublisher<>(Mono.just(response),
                                IndexImageResponse.class,
                                elasticResponse.getHttpStatus());
                    } catch (IOException e) {
                        return new ResponsePublisher<>(Mono.just(new ErrorMessage("Unable to parse response from elastic")),
                                ErrorMessage.class,
                                HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                }
        );
    }

    public static class IndexImageRequest {
        public String imageUrl;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            IndexImageRequest that = (IndexImageRequest) o;

            return imageUrl != null ? imageUrl.equals(that.imageUrl) : that.imageUrl == null;
        }

        @Override
        public int hashCode() {
            return imageUrl != null ? imageUrl.hashCode() : 0;
        }

        public IndexImageRequest setUrl(String url) {
            this.imageUrl = url;
            return this;
        }
    }


    public static class ErrorMessage {
        public String message;

        public ErrorMessage(String message) {
            this.message = message;
        }
    }

    public static class ResponsePublisher<T> {
        Mono<T> resultMono;
        Class<T> responseClass;
        HttpStatus status;

        public ResponsePublisher(Mono<T> resultMono, Class<T> responseClass, HttpStatus status) {

            this.resultMono = resultMono;
            this.responseClass = responseClass;
            this.status = status;
        }
    }

    public static class IndexImageResponse {
        public String _id;
    }
}