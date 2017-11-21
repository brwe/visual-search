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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import visualsearch.image.ProcessImage;
import visualsearch.image.ProcessedImage;
import visualsearch.service.ResponsePublisher;
import visualsearch.service.services.ElasticService;
import visualsearch.service.services.ImageRetrieveService;

import java.io.IOException;
import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static visualsearch.service.HelperMethods.DUMMY_IMAGE_URL;
import static visualsearch.service.HelperMethods.createElasticPutResponse;
import static visualsearch.service.HelperMethods.getImageClientResponse;

public class IndexImageHandlerTest {

    @Test
    public void testJsonResponseContainsId() throws IOException {
        // mock the image retrieval
        ImageRetrieveService.FetchImageRequest fetchImageRequest = new ImageRetrieveService.FetchImageRequest(DUMMY_IMAGE_URL);
        Mono<ImageRetrieveService.ImageResponse> imageResponse = getImageClientResponse(Duration.ZERO);
        ImageRetrieveService imageRetrieveService = mock(ImageRetrieveService.class);
        doReturn(imageResponse)
                .when(imageRetrieveService).fetchImage(fetchImageRequest);

        // mock elasticsearch
        ProcessedImage processedImage = ProcessImage.getProcessingResult(imageResponse.block().body(), ProcessedImage.builder().imageUrl(fetchImageRequest.imageUrl));
        String elasticId = "123";
        String storedBody = new ObjectMapper()
                .writeValueAsString(processedImage);
        ElasticService elasticService = mock(ElasticService.class);
        doReturn(createElasticPutResponse(Duration.ZERO, HttpStatus.CREATED, elasticId))
                .when(elasticService).post(storedBody);


        // now check that the response actually conatains the id
        IndexImageHandler imageHandler = new IndexImageHandler(imageRetrieveService, elasticService);
        IndexImageRequest indexImageRequest = new IndexImageRequest();
        indexImageRequest.imageUrl = DUMMY_IMAGE_URL;
        ResponsePublisher imageIndexServerResponse = imageHandler.computeResponse(Mono.just(indexImageRequest)).block();
        assertThat(imageIndexServerResponse.responseClass, equalTo(IndexImageResponse.class));
        assertThat(imageIndexServerResponse.resultMono.block(), instanceOf(IndexImageResponse.class));
        IndexImageResponse response = (IndexImageResponse) imageIndexServerResponse.resultMono.block();
        assertThat(response._id, equalTo(elasticId));
    }

    @Test
    public void testImageRetrieveException() throws IOException {
        // mock image retrieval to throw
        ImageRetrieveService.FetchImageRequest fetchImageRequest = new ImageRetrieveService.FetchImageRequest(DUMMY_IMAGE_URL);
        ImageRetrieveService imageRetrieveService = mock(ImageRetrieveService.class);
        doThrow(new IllegalArgumentException("No can do.")).
                when(imageRetrieveService).fetchImage(fetchImageRequest);

        // test that exception is actually caught
        IndexImageHandler imageHandler = new IndexImageHandler(imageRetrieveService, null);
        IndexImageRequest indexImageRequest = new IndexImageRequest();
        indexImageRequest.imageUrl = DUMMY_IMAGE_URL;
        ResponsePublisher imageIndexServerResponse = imageHandler.computeResponse(Mono.just(indexImageRequest)).block();
        assertThat(imageIndexServerResponse.responseClass, equalTo(IndexImageHandler.ErrorMessage.class));
        assertThat(imageIndexServerResponse.resultMono.block(), instanceOf(IndexImageHandler.ErrorMessage.class));
        IndexImageHandler.ErrorMessage response = (IndexImageHandler.ErrorMessage) imageIndexServerResponse.resultMono.block();
        assertThat(response.message, equalTo("java.lang.IllegalArgumentException: No can do."));
    }

    @Test
    public void testImageRetrieve302() throws IOException {
        // mock image retrieval to get 302
        ImageRetrieveService.FetchImageRequest fetchImageRequest = new ImageRetrieveService.FetchImageRequest(DUMMY_IMAGE_URL);
        ImageRetrieveService imageRetrieveService = mock(ImageRetrieveService.class);
        doReturn(getImageClientResponse(Duration.ZERO, HttpStatus.FOUND))
                .when(imageRetrieveService).fetchImage(fetchImageRequest);

        // test that 302 from image is returned
        IndexImageHandler imageHandler = new IndexImageHandler(imageRetrieveService, null);
        IndexImageRequest indexImageRequest = new IndexImageRequest();
        indexImageRequest.imageUrl = DUMMY_IMAGE_URL;
        ResponsePublisher imageIndexServerResponse = imageHandler.computeResponse(Mono.just(indexImageRequest)).block();
        assertThat(imageIndexServerResponse.responseClass, equalTo(IndexImageHandler.ErrorMessage.class));
        assertThat(imageIndexServerResponse.resultMono.block(), instanceOf(IndexImageHandler.ErrorMessage.class));
        IndexImageHandler.ErrorMessage response = (IndexImageHandler.ErrorMessage) imageIndexServerResponse.resultMono.block();
        assertThat(response.message, equalTo("Could not fetch image."));
    }

}