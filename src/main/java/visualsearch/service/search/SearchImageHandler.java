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

package visualsearch.service.search;

import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import visualsearch.image.ProcessImage;
import visualsearch.image.ProcessedImage;
import visualsearch.service.Handler;
import visualsearch.service.ResponsePublisher;
import visualsearch.service.services.ElasticService;
import visualsearch.service.services.ImageRetrieveService;

import java.io.IOException;
import java.nio.ByteBuffer;

@Component
public class SearchImageHandler extends Handler<SearchImageRequest, String> {


    public SearchImageHandler(ImageRetrieveService imageRetrieveService, ElasticService elasticService) {
        super(imageRetrieveService, elasticService, SearchImageRequest.class);
    }

    @Override
    protected Mono<ResponsePublisher> computeResponse(Mono<SearchImageRequest> searchImageRequestMono) {
        ProcessedImage.Builder resultBuilder = ProcessedImage.builder();
        return searchImageRequestMono.flatMap(searchImageRequest -> {
                    if (searchImageRequest.imageUrl == null) {
                        return Mono.just(new ResponsePublisher<>(Mono.just(new ErrorMessage("imageUrl was not specified in request")),
                                ErrorMessage.class,
                                HttpStatus.BAD_REQUEST));
                    } else {
                        resultBuilder.imageUrl(searchImageRequest.imageUrl);
                        try {
                            logger.debug("Processing image " + searchImageRequest.imageUrl);
                            return imageRetrieveService.fetchImage(new ImageRetrieveService.FetchImageRequest(searchImageRequest.imageUrl))
                                    .flatMap(clientResponse -> {
                                        if (clientResponse.statusCode() != HttpStatus.OK) {
                                            return Mono.just(new ResponsePublisher<>(Mono.just(new ErrorMessage("fetching image returned error")),
                                                    ErrorMessage.class,
                                                    clientResponse.statusCode()));
                                        } else {
                                            logger.debug("got response for image " + searchImageRequest.imageUrl);
                                            return processAndSearchImage(clientResponse.body(), resultBuilder);

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


    private Mono<ResponsePublisher> processAndSearchImage(ByteBuffer byteBuffer, ProcessedImage.Builder builder) {
        logger.debug("processing bytes for image " + builder.build().imageUrl);
        ProcessedImage processedImage = ProcessImage.getProcessingResult(byteBuffer, builder);
        logger.debug("processed bytes for image " + builder.build().imageUrl);
        return searchSimilarImagesInElasticsearch(processedImage);
    }

    private Mono<ResponsePublisher> searchSimilarImagesInElasticsearch(ProcessedImage processedImage) {
        String queryBody;
        queryBody = generateQuery(processedImage);

        return elasticService.search(queryBody).map(
                elasticResponse -> {
                    logger.debug("stored visualsearch.image " + processedImage.imageUrl);
                    if (elasticResponse.getHttpStatus() != HttpStatus.OK) {
                        return new ResponsePublisher<>(Mono.just(new ErrorMessage("Unable to search image in elastic")),
                                ErrorMessage.class,
                                elasticResponse.getHttpStatus());
                    }
                    // get the results from search response
                    try {
                        return new ResponsePublisher<>(Mono.just(elasticResponse.getBody()),
                                String.class,
                                elasticResponse.getHttpStatus());
                    } catch (IOException e) {
                        return new ResponsePublisher<>(Mono.just(new ErrorMessage("Unable to parse response from elastic")),
                                ErrorMessage.class,
                                HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                }
        );
    }

    public static String generateQuery(ProcessedImage processedImage) {
        double scale = processedImage.receivedBytes / 4.0;
        assert scale > 0.0;
        String jsonString = new JSONObject()
                .put("query", new JSONObject()
                        .put("function_score", new JSONObject()
                                .put("functions", new JSONObject[]{
                                        new JSONObject()
                                                .put("gauss", new JSONObject().put("receivedBytes", new JSONObject()
                                                .put("origin", processedImage.receivedBytes)
                                                .put("scale", scale))
                                        )
                                })
                        )

                )
                .toString();


        return jsonString;
    }

}