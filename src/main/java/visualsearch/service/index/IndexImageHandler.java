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

package visualsearch.service.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyExtractor;
import org.springframework.web.reactive.function.BodyExtractors;
import reactor.core.publisher.Mono;
import visualsearch.image.ProcessImage;
import visualsearch.image.ProcessedImage;
import visualsearch.service.services.ElasticService;
import visualsearch.service.Handler;
import visualsearch.service.services.ImageRetrieveService;
import visualsearch.service.ResponsePublisher;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

@Component
public class IndexImageHandler extends Handler<IndexImageRequest, IndexImageResponse> {

    final BodyExtractor<Mono<IndexImageRequest>, ReactiveHttpInputMessage> requestExtractor = BodyExtractors.toMono(IndexImageRequest.class);

    public IndexImageHandler(ImageRetrieveService imageRetrieveService, ElasticService elasticService) {
        super(imageRetrieveService, elasticService, IndexImageRequest.class);
    }

    @Override
    protected Mono<ResponsePublisher> computeResponse(Mono<IndexImageRequest> indexImageRequestMono) {
        ProcessedImage.Builder resultBuilder = ProcessedImage.builder();
        return indexImageRequestMono.flatMap(indexImageRequest -> {
                    if (indexImageRequest.imageUrl == null) {
                        return Mono.just(new ResponsePublisher<>(Mono.just(new ErrorMessage("imageUrl was not specified in request")),
                                ErrorMessage.class,
                                HttpStatus.BAD_REQUEST));
                    } else {
                        resultBuilder.imageUrl(indexImageRequest.imageUrl);
                        try {
                            logger.debug("Processing image " + indexImageRequest.imageUrl);
                            return imageRetrieveService.fetchImage(new ImageRetrieveService.FetchImageRequest(indexImageRequest.imageUrl))
                                    .flatMap(clientResponse -> {
                                        if (clientResponse.statusCode() != HttpStatus.OK) {
                                            return Mono.just(new ResponsePublisher<>(Mono.just(new ErrorMessage("fetching image returned error")),
                                                    ErrorMessage.class,
                                                    clientResponse.statusCode()));
                                        } else {
                                            logger.debug("got response for image " + indexImageRequest.imageUrl);
                                            return processAndStoreImage(clientResponse.body(), resultBuilder);

                                        }
                                    });
                        } catch (Exception e) {
                            return Mono.just(new ResponsePublisher<>(Mono.just(new ErrorMessage("fetching image failed: " + e.getMessage())),
                                    ErrorMessage.class,
                                    HttpStatus.INTERNAL_SERVER_ERROR));
                        }
                    }
                }
        );
    }


    private Mono<ResponsePublisher> processAndStoreImage(ByteBuffer byteBuffer, ProcessedImage.Builder builder) {
        logger.debug("processing bytes for image " + builder.build().imageUrl);
        ProcessedImage processedImage = ProcessImage.getProcessingResult(byteBuffer, builder);
        logger.debug("processed bytes for image " + builder.build().imageUrl);
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

}