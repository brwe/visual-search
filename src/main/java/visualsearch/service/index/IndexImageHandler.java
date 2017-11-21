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
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import visualsearch.image.ProcessedImage;
import visualsearch.service.Handler;
import visualsearch.service.ResponsePublisher;
import visualsearch.service.services.ElasticService;
import visualsearch.service.services.ImageRetrieveService;

import java.io.IOException;
import java.util.HashMap;

@Component
public class IndexImageHandler extends Handler<IndexImageRequest, IndexImageResponse> {

    public IndexImageHandler(ImageRetrieveService imageRetrieveService, ElasticService elasticService) {
        super(imageRetrieveService, elasticService, IndexImageRequest.class);
    }

    @Override
    protected Mono<ResponsePublisher> computeResponse(Mono<IndexImageRequest> indexImageRequestMono) {
        return indexImageRequestMono
                .flatMap(searchImageRequest ->
                        fetchImage(searchImageRequest.imageUrl))
                .map(imageResponse -> processImage(imageResponse))
                .flatMap(processedImage -> storeResultInElasticsearch(processedImage))
                .map(result -> convertEsResponseToResponse(result))
                .onErrorResume(t -> handleError(t));
    }


    private Mono<ElasticService.ElasticResponse> storeResultInElasticsearch(ProcessedImage processedImage) {
        String documentBody = imageToJsonDocument(processedImage);
        Mono<ElasticService.ElasticResponse> elasticResponseMono = elasticService.post(documentBody);
        return elasticResponseMono.map(elasticResponse -> {
            if (elasticResponse.getHttpStatus() != HttpStatus.CREATED) {
                throw new RequestFailedException(elasticResponse.getHttpStatus(), "Cannot write to elastic.");
            }
            return elasticResponse;
        });
    }

    private ResponsePublisher convertEsResponseToResponse(ElasticService.ElasticResponse elasticResponse) {
        IndexImageResponse response = createResponse(elasticResponse);
        return new ResponsePublisher<>(Mono.just(response),
                IndexImageResponse.class,
                elasticResponse.getHttpStatus());
    }

    private IndexImageResponse createResponse(ElasticService.ElasticResponse elasticResponse) {
        HashMap<String, Object> result;
        try {
            result = new ObjectMapper().readValue(elasticResponse.getBody(), HashMap.class);
        } catch (IOException e) {
            throw new RequestFailedException(HttpStatus.INTERNAL_SERVER_ERROR, e, "Cannot parse elastic response.");
        }
        String id = (String) result.get("_id");
        IndexImageResponse response = new IndexImageResponse();
        response._id = id;
        return response;
    }

    private String imageToJsonDocument(ProcessedImage processedImage) {
        try {
            return new ObjectMapper().writeValueAsString(processedImage);
        } catch (JsonProcessingException e) {
            throw new RequestFailedException(HttpStatus.INTERNAL_SERVER_ERROR, e, "Could not serialize processed image: ");
        }
    }

}