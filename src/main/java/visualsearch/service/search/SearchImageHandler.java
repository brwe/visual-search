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
    protected Mono<ResponsePublisher> computeResponse(Mono<SearchImageRequest> searchImageRequestMono) {
        ProcessedImage.Builder resultBuilder = ProcessedImage.builder();
        return searchImageRequestMono.flatMap(searchImageRequest -> {
            if (searchImageRequest.imageUrl == null) {
                return monoErrorMessage("imageUrl was not specified in request", HttpStatus.BAD_REQUEST);
            } else {
                resultBuilder.imageUrl(searchImageRequest.imageUrl);
                try {
                    Mono<ImageRetrieveService.ImageResponse> imageResponseMono = imageRetrieveService.fetchImage(new ImageRetrieveService.FetchImageRequest(searchImageRequest.imageUrl));
                    return imageResponseMono.flatMap(clientResponse -> {
                        if (clientResponse.statusCode() != HttpStatus.OK) {
                            return monoErrorMessage("fetching image returned error", clientResponse.statusCode());
                        } else {
                            ProcessedImage processedImage = null;
                            try {
                                processedImage = ProcessImage.getProcessingResult(clientResponse.body(), resultBuilder);
                            } catch (IOException e) {
                                return monoErrorMessage("Unable to handle image: " + e.toString(), HttpStatus.INTERNAL_SERVER_ERROR);
                            }
                            return searchSimilarImagesInElasticsearch(processedImage);
                        }
                    });
                } catch (Exception e) {
                    return monoErrorMessage("fetching image failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
        });
    }


    private Mono<ResponsePublisher> searchSimilarImagesInElasticsearch(ProcessedImage processedImage) {
        String queryBody;
        queryBody = generateQuery(processedImage);

        Mono<ElasticService.ElasticResponse> elasticResponseMono = elasticService.search(queryBody);
        return elasticResponseMono.map(elasticResponse -> {
            if (elasticResponse.getHttpStatus() != HttpStatus.OK) {
                return errorMessage("Unable to search image in elastic", elasticResponse.getHttpStatus());
            }
            // get the results from search response
            try {
                return new ResponsePublisher<>(Mono.just(elasticResponse.getBody()),
                        String.class,
                        elasticResponse.getHttpStatus());
            } catch (IOException e) {
                return errorMessage("Unable to parse response from elastic: " + e.toString(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        });
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

}