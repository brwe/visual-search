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

import org.junit.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import visualsearch.image.ProcessImage;
import visualsearch.image.ProcessedImage;
import visualsearch.service.services.ElasticService;
import visualsearch.service.services.ImageRetrieveService;

import java.io.IOException;
import java.time.Duration;

import static junit.framework.TestCase.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static visualsearch.service.HelperMethods.DUMMY_IMAGE_URL;
import static visualsearch.service.HelperMethods.createElasticSearchResponse;
import static visualsearch.service.HelperMethods.getImageClientResponse;

public class SearchImageHandlerTest {

    @Test
    public void testElasticsearchResponseIsReturned() throws IOException {
        SearchImageRequest searchImageRequest = new SearchImageRequest(DUMMY_IMAGE_URL, 10);

        // mock the image retrieval
        ImageRetrieveService.FetchImageRequest fetchImageRequest = new ImageRetrieveService.FetchImageRequest(DUMMY_IMAGE_URL);
        Mono<ImageRetrieveService.ImageFetchResponse> imageResponse = getImageClientResponse(Duration.ZERO);
        ImageRetrieveService imageRetrieveService = mock(ImageRetrieveService.class);
        doReturn(imageResponse)
                .when(imageRetrieveService).fetchImage(fetchImageRequest);

        // mock elasticsearch
        ProcessedImage processedImage = ProcessImage.getProcessingResult(imageResponse.block().body(), ProcessedImage.builder().imageUrl(fetchImageRequest.imageUrl));
        String queryBody = SearchImageHandler.generateQuery(processedImage, searchImageRequest);
        ElasticService elasticService = mock(ElasticService.class);
        doReturn(createElasticSearchResponse(Duration.ZERO, HttpStatus.OK))
                .when(elasticService).search(queryBody);

        SearchImageHandler imageHandler = new SearchImageHandler(imageRetrieveService, elasticService);

        SearchImageResponse searchImageResponse = imageHandler.computeResponse(Mono.just(searchImageRequest)).block();
        assertThat(searchImageResponse.response, equalTo("{ this is really irrelevant because we only pass on the elasticsearch response here }"));
    }

    @Test
    public void testImageRetrieveException() throws IOException {
        SearchImageRequest searchImageRequest = new SearchImageRequest(DUMMY_IMAGE_URL, 10);

        // mock image retrieval
        ImageRetrieveService.FetchImageRequest fetchImageRequest = new ImageRetrieveService.FetchImageRequest(DUMMY_IMAGE_URL);
        ImageRetrieveService imageRetrieveService = mock(ImageRetrieveService.class);
        doThrow(new IllegalArgumentException("No can do.")).
                when(imageRetrieveService).fetchImage(fetchImageRequest);
        SearchImageHandler imageHandler = new SearchImageHandler(imageRetrieveService, null);

        try {
            imageHandler.computeResponse(Mono.just(searchImageRequest)).block();
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("No can do."));
        }
    }
}