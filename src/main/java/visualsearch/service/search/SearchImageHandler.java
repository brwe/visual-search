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
import visualsearch.image.ProcessedImage;
import visualsearch.service.Handler;
import visualsearch.service.services.ElasticService;
import visualsearch.service.services.ImageRetrieveService;

@Component
public class SearchImageHandler extends Handler<SearchImageRequest, SearchImageResponse> {

    public SearchImageHandler(ImageRetrieveService imageRetrieveService, ElasticService elasticService) {
        super(imageRetrieveService, elasticService, SearchImageRequest.class);
    }

    @Override
    protected Mono<SearchImageResponse> computeResponse(Mono<SearchImageRequest> searchImageRequestMono) {
        return searchImageRequestMono
                .flatMap(searchImageRequest ->
                        fetchImage(searchImageRequest.imageUrl))
                .map(imageResponse -> processImage(imageResponse))
                .flatMap(processedImage -> searchSimilarImages(processedImage));
    }

    private Mono<SearchImageResponse> searchSimilarImages(ProcessedImage processedImage) {
        String queryBody = generateQuery(processedImage);
        Mono<ElasticService.ElasticResponse> elasticResponseMono = elasticService.search(queryBody);
        return elasticResponseMono.map(elasticResponse -> {
            if (elasticResponse.getHttpStatus() != HttpStatus.OK) {
                throw new RequestFailedException(elasticResponse.getHttpStatus(), "Could not query elasticsearch: ");
            }
            String elasticsearchResult = getElasticsearchResponse(elasticResponse);
            SearchImageResponse searchImageResponse = new SearchImageResponse();
            searchImageResponse.response = elasticsearchResult;
            return searchImageResponse;
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