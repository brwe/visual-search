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

import java.util.concurrent.atomic.AtomicReference;

@Component
public class SearchImageHandler extends Handler<SearchImageRequest, SearchImageResponse> {

    public SearchImageHandler(ImageRetrieveService imageRetrieveService, ElasticService elasticService) {
        super(imageRetrieveService, elasticService, SearchImageRequest.class);
    }

    @Override
    protected Mono<SearchImageResponse> computeResponse(Mono<SearchImageRequest> searchImageRequestMono) {
        AtomicReference<SearchImageRequest> searchImageRequestAtomicReference = new AtomicReference<>();
        return searchImageRequestMono
                .flatMap(searchImageRequest -> {
                    searchImageRequestAtomicReference.set(searchImageRequest);
                    return fetchImage(searchImageRequest.imageUrl);
                })
                .map(imageResponse -> processImage(imageResponse))
                .flatMap(processedImage -> searchSimilarImages(processedImage, searchImageRequestAtomicReference));
    }

    private Mono<SearchImageResponse> searchSimilarImages(ProcessedImage processedImage, AtomicReference<SearchImageRequest> searchImageRequestAtomicReference) {
        SearchImageRequest searchImageRequest = searchImageRequestAtomicReference.get();
        String queryBody = generateQuery(processedImage, searchImageRequest);
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

    public static String generateQuery(ProcessedImage processedImage, SearchImageRequest searchImageRequest) {
        JSONObject[] matchCauses = new JSONObject[processedImage.dHash.size()];
        int i = 0;
        for (String key : processedImage.dHash.keySet()) {
            matchCauses[i] =
                    new JSONObject()
                            .put("constant_score", new JSONObject()
                                    .put("query", new JSONObject()
                                            .put("match",
                                                    new JSONObject()
                                                            .put("dHash." + key, processedImage.dHash.get(key))
                                            )));
            i++;
        }
        String jsonString = new JSONObject()
                .put("query", new JSONObject()
                        .put("bool", new JSONObject()
                                .put("minimum_should_match", searchImageRequest.minimumShouldMatch)
                                .put("should", matchCauses))
                )
                .toString();
        return jsonString;
    }

}