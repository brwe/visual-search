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
import visualsearch.image.ProcessImage;
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
        return indexImageRequestMono.flatMap(indexImageRequest -> {
            if (indexImageRequest.imageUrl == null) {
                return monoErrorMessage("imageUrl was not specified in request", HttpStatus.BAD_REQUEST);
            } else {
                try {
                    Mono<ImageRetrieveService.ImageResponse> imageResponseMono = imageRetrieveService.fetchImage(new ImageRetrieveService.FetchImageRequest(indexImageRequest.imageUrl));
                    return imageResponseMono.flatMap(clientResponse -> {
                        if (clientResponse.statusCode() != HttpStatus.OK) {
                            return monoErrorMessage("fetching image returned error", clientResponse.statusCode());
                        } else {
                            try {
                                ProcessedImage.Builder resultBuilder = ProcessedImage.builder();
                                resultBuilder.imageUrl(indexImageRequest.imageUrl);
                                ProcessedImage processedImage = ProcessImage.getProcessingResult(clientResponse.body(), resultBuilder);
                                return storeResultInElasticsearch(processedImage);
                            } catch (IOException e) {
                                return monoErrorMessage("Unable to handle image: " + e.toString(), HttpStatus.INTERNAL_SERVER_ERROR);
                            }
                        }
                    });
                } catch (Exception e) {
                    return monoErrorMessage("fetching image failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
        });
    }


    private Mono<ResponsePublisher> storeResultInElasticsearch(ProcessedImage processedImage) {
        String documentBody;
        try {
            documentBody = imageToJsonDocument(processedImage);
        } catch (JsonProcessingException e) {
            return monoErrorMessage("Unable to serialize image: " + e.toString(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        Mono<ElasticService.ElasticResponse> elasticResponseMono = elasticService.post(documentBody);
        return elasticResponseMono.map(elasticResponse -> {
            if (elasticResponse.getHttpStatus() != HttpStatus.CREATED) {
                return errorMessage("Unable to write visualsearch.image processing result to elastic", elasticResponse.getHttpStatus());
            }
            // get the id from request
            try {
                IndexImageResponse response = convertEsResponseToResponse(elasticResponse);
                return new ResponsePublisher<>(Mono.just(response),
                        IndexImageResponse.class,
                        elasticResponse.getHttpStatus());
            } catch (IOException e) {
                return errorMessage("Unable to parse response from elastic: " + e.toString(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        });
    }

    private IndexImageResponse convertEsResponseToResponse(ElasticService.ElasticResponse elasticResponse) throws IOException {
        HashMap<String, Object> result =
                new ObjectMapper().readValue(elasticResponse.getBody(), HashMap.class);
        String id = (String) result.get("_id");
        IndexImageResponse response = new IndexImageResponse();
        response._id = id;
        return response;
    }

    private String imageToJsonDocument(ProcessedImage processedImage) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(processedImage);
    }

}