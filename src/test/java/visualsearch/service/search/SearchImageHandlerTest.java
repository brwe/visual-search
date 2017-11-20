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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
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
import static visualsearch.service.HelperMethods.createElasticPutResponse;
import static visualsearch.service.HelperMethods.createElasticSearchResponse;
import static visualsearch.service.HelperMethods.getImageClientResponse;

public class SearchImageHandlerTest {

    @Test
    public void testQueryFromProcessedImage() {
        String imageUrl = "https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg";
        String query = SearchImageHandler.generateQuery(ProcessedImage.builder().imageUrl(imageUrl).capacity(20).build());
        assertThat(query, equalTo("{\"query\":{\"function_score\":{\"functions\":[{\"gauss\":{\"receivedBytes\":{\"origin\":20,\"scale\":5}}}]}}}"));
    }

    @Test
    public void testCanHandleBytesLessThat4QueryFromProcessedImage() {
        String imageUrl = "https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg";
        String query = SearchImageHandler.generateQuery(ProcessedImage.builder().imageUrl(imageUrl).capacity(3).build());
        assertThat(query, equalTo("{\"query\":{\"function_score\":{\"functions\":[{\"gauss\":{\"receivedBytes\":{\"origin\":3,\"scale\":0.75}}}]}}}"));
    }

    @Test
    public void testJsonResponseContainsId() throws IOException {
        String imageUrl = "https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg";
        ImageRetrieveService.FetchImageRequest fetchImageRequest = new ImageRetrieveService.FetchImageRequest(imageUrl);
        String query = SearchImageHandler.generateQuery(ProcessedImage.builder().imageUrl(imageUrl).capacity(11389).numPixels(40000).build());
        ElasticService elasticService = mock(ElasticService.class);
        doReturn(createElasticSearchResponse(Duration.ZERO, HttpStatus.OK))
                .when(elasticService).search(query);

        ImageRetrieveService imageRetrieveService = mock(ImageRetrieveService.class);
        doReturn(getImageClientResponse(Duration.ZERO))
                .when(imageRetrieveService).fetchImage(fetchImageRequest);
        SearchImageHandler imageHandler = new SearchImageHandler(imageRetrieveService, elasticService);

        SearchImageRequest indexImageRequest = new SearchImageRequest();
        indexImageRequest.imageUrl = imageUrl;
        ResponsePublisher imageIndexServerResponse = imageHandler.computeResponse(Mono.just(indexImageRequest)).block();
        assertThat(imageIndexServerResponse.responseClass, equalTo(String.class));
        assertThat(imageIndexServerResponse.resultMono.block(), instanceOf(String.class));
        String response = (String) imageIndexServerResponse.resultMono.block();
        assertThat(response, equalTo("{ this is really irrelevant because we only pass on the elasticsearch response here }"));
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
        SearchImageHandler imageHandler = new SearchImageHandler(imageRetrieveService, elasticService);

        SearchImageRequest indexImageRequest = new SearchImageRequest();
        indexImageRequest.imageUrl = "https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg";
        ResponsePublisher imageIndexServerResponse = imageHandler.computeResponse(Mono.just(indexImageRequest)).block();
        assertThat(imageIndexServerResponse.responseClass, equalTo(SearchImageHandler.ErrorMessage.class));
        assertThat(imageIndexServerResponse.resultMono.block(), instanceOf(SearchImageHandler.ErrorMessage.class));
        SearchImageHandler.ErrorMessage response = (SearchImageHandler.ErrorMessage) imageIndexServerResponse.resultMono.block();
        assertThat(response.message, equalTo("fetching image failed: No can do."));
    }
}