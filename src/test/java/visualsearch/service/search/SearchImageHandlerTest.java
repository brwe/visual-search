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
import visualsearch.image.ProcessedImage;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import visualsearch.service.ResponsePublisher;
import visualsearch.service.index.IndexImageHandler;
import visualsearch.service.index.IndexImageRequest;
import visualsearch.service.index.IndexImageResponse;
import visualsearch.service.services.ElasticService;
import visualsearch.service.services.ImageRetrieveService;

import java.io.IOException;
import java.time.Duration;

import static visualsearch.service.HelperMethods.createElasticPutResponse;
import static visualsearch.service.HelperMethods.getImageClientResponse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class IndexImageHandlerTest {

    @Test
    public void testJsonResponseContainsId() throws IOException {
        ImageRetrieveService.FetchImageRequest fetchImageRequest = new ImageRetrieveService.FetchImageRequest("https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg");
        String elasticId = "123";
        String storedBody = new ObjectMapper()
                .writeValueAsString(ProcessedImage.builder().capacity(3).imageUrl(fetchImageRequest.imageUrl).build());
        ElasticService elasticService = mock(ElasticService.class);
        doReturn(createElasticPutResponse(Duration.ZERO, HttpStatus.CREATED, elasticId))
                .when(elasticService).post(storedBody);

        ImageRetrieveService imageRetrieveService = mock(ImageRetrieveService.class);
        doReturn(getImageClientResponse(Duration.ZERO))
                .when(imageRetrieveService).fetchImage(fetchImageRequest);
        IndexImageHandler imageHandler = new IndexImageHandler(imageRetrieveService, elasticService);

        IndexImageRequest indexImageRequest = new IndexImageRequest();
        indexImageRequest.imageUrl = "https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg";
        ResponsePublisher imageIndexServerResponse = imageHandler.computeResponse(Mono.just(indexImageRequest)).block();
        assertThat(imageIndexServerResponse.responseClass, equalTo(IndexImageResponse.class));
        assertThat(imageIndexServerResponse.resultMono.block(), instanceOf(IndexImageResponse.class));
        IndexImageResponse response = (IndexImageResponse) imageIndexServerResponse.resultMono.block();
        assertThat(response._id, equalTo(elasticId));
    }

    @Test
    public void testImageRetrieveException() throws IOException {
        ImageRetrieveService.FetchImageRequest fetchImageRequest = new ImageRetrieveService.FetchImageRequest("https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg");
        String elasticId = "123";
        String storedBody = new ObjectMapper()
                .writeValueAsString(ProcessedImage.builder().capacity(20).imageUrl(fetchImageRequest.imageUrl).build());
        ElasticService elasticService = mock(ElasticService.class);
        doReturn(createElasticPutResponse(Duration.ZERO, HttpStatus.CREATED, elasticId))
                .when(elasticService).post(storedBody);

        ImageRetrieveService imageRetrieveService = mock(ImageRetrieveService.class);
        doThrow(new IllegalArgumentException("No can do.")).
                when(imageRetrieveService).fetchImage(fetchImageRequest);
        IndexImageHandler imageHandler = new IndexImageHandler(imageRetrieveService, elasticService);

        IndexImageRequest indexImageRequest = new IndexImageRequest();
        indexImageRequest.imageUrl = "https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg";
        ResponsePublisher imageIndexServerResponse = imageHandler.computeResponse(Mono.just(indexImageRequest)).block();
        assertThat(imageIndexServerResponse.responseClass, equalTo(IndexImageHandler.ErrorMessage.class));
        assertThat(imageIndexServerResponse.resultMono.block(), instanceOf(IndexImageHandler.ErrorMessage.class));
        IndexImageHandler.ErrorMessage response = (IndexImageHandler.ErrorMessage) imageIndexServerResponse.resultMono.block();
        assertThat(response.message, equalTo("fetching image failed: No can do."));
    }

}