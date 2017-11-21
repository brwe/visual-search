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

@Component
public class SearchImageHandler extends Handler<SearchImageRequest, String> {


    public SearchImageHandler(ImageRetrieveService imageRetrieveService, ElasticService elasticService) {
        super(imageRetrieveService, elasticService, SearchImageRequest.class);
    }

    @Override
    protected Mono<ResponsePublisher> computeResponse(Mono<SearchImageRequest> searchImageRequest) {
        Mono<ImageRetrieveService.ImageResponse> imageResponse = fetchImage(searchImageRequest);
        Mono<ProcessedImage> processedImage = processImage(imageResponse);
        Mono<String> elasticsearchResult = searchSimilarImages(processedImage);
        return createResult(elasticsearchResult);
    }

    private Mono<ImageRetrieveService.ImageResponse> fetchImage(Mono<SearchImageRequest> searchImageRequestMono) {
        return searchImageRequestMono.flatMap(searchImageRequest -> {
            if (searchImageRequest.imageUrl == null) {
                throw new RequestFailedException(HttpStatus.BAD_REQUEST, "imageUrl was not specified in request.");
            } else {
                return imageRetrieveService.fetchImage(new ImageRetrieveService.FetchImageRequest(searchImageRequest.imageUrl));
            }
        });
    }

    private Mono<ProcessedImage> processImage(Mono<ImageRetrieveService.ImageResponse> imageResponseMono) {
        return imageResponseMono.map(imageResponse -> {
            if (imageResponse.statusCode() != HttpStatus.OK) {
                throw new RequestFailedException(imageResponse.statusCode(), "Could not fetch image.");
            } else {
                ProcessedImage.Builder resultBuilder = ProcessedImage.builder();
                resultBuilder.imageUrl(imageResponse.imageUrl());
                return processImage(imageResponse, resultBuilder);
            }
        });
    }

    private Mono<String> searchSimilarImages(Mono<ProcessedImage> processedImageMono) {
        return processedImageMono.flatMap(processedImage -> {
            String queryBody = generateQuery(processedImage);
            Mono<ElasticService.ElasticResponse> elasticResponseMono = elasticService.search(queryBody);
            return elasticResponseMono.map(elasticResponse -> {
                if (elasticResponse.getHttpStatus() != HttpStatus.OK) {
                    throw new RequestFailedException(elasticResponse.getHttpStatus(), "Could not query elasticsearch: ");
                }
                String elasticsearchResult = getElasticsearchResponse(elasticResponse);
                return elasticsearchResult;
            });
        });
    }

    private Mono<ResponsePublisher> createResult(Mono<String> elasticsearchResult) {
        return elasticsearchResult.map(result -> new ResponsePublisher(Mono.just(result), String.class, HttpStatus.OK))
                .onErrorResume(t -> {
                    if (t instanceof RequestFailedException) {
                        RequestFailedException requestFailedException = (RequestFailedException) t;
                        return monoErrorMessage(requestFailedException.getMessage(), requestFailedException.httpStatus);
                    } else {
                        return monoErrorMessage(t.toString(), HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                });
    }


    ProcessedImage processImage(ImageRetrieveService.ImageResponse imageResponse, ProcessedImage.Builder resultBuilder) {
        try {
            return ProcessImage.getProcessingResult(imageResponse.body(), resultBuilder);
        } catch (IOException e) {
            throw new RequestFailedException(HttpStatus.INTERNAL_SERVER_ERROR, e, "Could not process image: ");
        }
    }


    private String getElasticsearchResponse(ElasticService.ElasticResponse elasticResponse) {
        try {
            return elasticResponse.getBody();
        } catch (IOException e) {
            throw new RequestFailedException(HttpStatus.INTERNAL_SERVER_ERROR, e, "Could not extract response from elasticsearch: ");
        }
    }

    public static String generateQuery(ProcessedImage processedImage) {
        double scale = processedImage.receivedBytes / 4.0;
        assert scale > 0.0;
        String jsonString = new JSONObject()
                .put("query", new JSONObject()
                        .put("function_score", new JSONObject()
                                .put("functions", new JSONObject[]{
                                        new JSONObject()
                                                .put("gauss", new JSONObject()
                                                .put("receivedBytes", new JSONObject()
                                                        .put("origin", processedImage.receivedBytes)
                                                        .put("scale", scale))
                                        )
                                })
                        )

                )
                .toString();
        return jsonString;
    }

    public static class RequestFailedException extends RuntimeException {
        private final HttpStatus httpStatus;
        private final Throwable t;
        private final String message;

        public RequestFailedException(HttpStatus httpStatus, Throwable t, String message) {
            this.t = t;
            this.httpStatus = httpStatus;
            this.message = message;
        }

        public RequestFailedException(HttpStatus httpStatus, String message) {
            this.t = null;
            this.httpStatus = httpStatus;
            this.message = message;
        }

        public HttpStatus getHttpStatus() {
            return httpStatus;
        }

        public String getMessage() {
            return message + (t == null ? "" : t.toString());
        }
    }

}