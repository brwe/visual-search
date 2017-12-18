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

package visualsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import visualsearch.image.ProcessImage;
import visualsearch.image.ProcessedImage;
import visualsearch.service.search.SearchImageHandler;
import visualsearch.service.search.SearchImageRequest;
import visualsearch.service.services.ElasticService;
import visualsearch.service.services.ImageRetrieveService;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doReturn;
import static visualsearch.service.HelperMethods.DUMMY_IMAGE_URL;
import static visualsearch.service.HelperMethods.createElasticPutResponse;
import static visualsearch.service.HelperMethods.createElasticSearchResponse;
import static visualsearch.service.HelperMethods.getImageClientResponse;
import static visualsearch.service.HelperMethods.getTestImageBytes;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ApiIntegTest {

    @Autowired
    private WebTestClient webClient;

    @MockBean
    private ElasticService elasticService;

    @MockBean
    private ImageRetrieveService imageRetrieveService;


    @Test
    @Ignore("Broke 23.11.2017 for no apparent reason")
    public void testActuatorStatus() {
        this.webClient.get().uri("/application/status").accept(MediaType.APPLICATION_JSON)
                .exchange().expectStatus().isOk().expectBody()
                .json("{\"status\":\"UP\"}");
    }

    @Test
    @Ignore("Not there despite: https://github.com/spring-projects/spring-boot/issues/7970")
    public void testActuatorTraceStatus() {
        this.webClient.get().uri("/application/trace").accept(MediaType.APPLICATION_JSON)
                .exchange().expectStatus().isOk().expectBody()
                .json("{\"traces\":[]}");
    }

    @Test
    public void testIndexImageNoFailures() throws IOException {
        ImageRetrieveService.FetchImageRequest fetchImageRequest = new ImageRetrieveService.FetchImageRequest(DUMMY_IMAGE_URL);
        Mono<ImageRetrieveService.ImageFetchResponse> imageResponseMono = getImageClientResponse(Duration.ZERO);
        doReturn(imageResponseMono)
                .when(imageRetrieveService).fetchImage(fetchImageRequest);

        ProcessedImage processedImage = ProcessImage.getProcessingResult(imageResponseMono.block().body(), ProcessedImage.builder().imageUrl(fetchImageRequest.imageUrl));
        String storedBody = new ObjectMapper().writeValueAsString(processedImage);
        doReturn(createElasticPutResponse(HttpStatus.CREATED))
                .when(elasticService).post(storedBody);

        String bodyString = this.webClient
                .post()
                .uri("/image")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"imageUrl\": \"" + DUMMY_IMAGE_URL + "\"}"), String.class)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        HashMap<String, Object> bodyMap = new ObjectMapper().readValue(bodyString, HashMap.class);
        assertThat(bodyMap.get("_id"), instanceOf(String.class));
    }

    @Test
    public void testIndexImageWithoutUrlNoFailures() throws IOException {
        byte[] imageBytes = getTestImageBytes();
        ProcessedImage processedImage = ProcessImage.getProcessingResult(ByteBuffer.wrap(imageBytes), ProcessedImage.builder().imageUrl("none"));
        String storedBody = new ObjectMapper().writeValueAsString(processedImage);
        doReturn(createElasticPutResponse(HttpStatus.CREATED))
                .when(elasticService).post(storedBody);

        String bodyString = this.webClient
                .post()
                .uri("/image")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"image\": \"" + Base64.getEncoder().encodeToString(imageBytes) + "\"}"), String.class)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        HashMap<String, Object> bodyMap = new ObjectMapper().readValue(bodyString, HashMap.class);
        assertThat(bodyMap.get("_id"), instanceOf(String.class));
    }

    @Test
    public void testImageResponseRelayed() throws IOException {
        ImageRetrieveService.FetchImageRequest fetchImageRequest = new ImageRetrieveService.FetchImageRequest(DUMMY_IMAGE_URL);
        doReturn(getImageClientResponse(Duration.ZERO, HttpStatus.NOT_FOUND))
                .when(imageRetrieveService).fetchImage(fetchImageRequest);
        this.webClient
                .post()
                .uri("/image")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"imageUrl\": \"" + DUMMY_IMAGE_URL + "\"}"), String.class)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class).isEqualTo("{\"message\":\"Could not fetch image.\"}");
    }

    @Test
    public void testIndexImageWithFaultyUrl() {
        this.webClient
                .post()
                .uri("/image")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"image_url1\": \"https://123.jpg\"}"), String.class)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
                .expectBody().jsonPath("message").isEqualTo("imageUrl was not specified in request.");
    }

    @Test
    public void testIndexImageParallel() throws InterruptedException, IOException {
        ImageRetrieveService.FetchImageRequest fetchImageRequest = new ImageRetrieveService.FetchImageRequest(DUMMY_IMAGE_URL);
        Mono<ImageRetrieveService.ImageFetchResponse> imageResponseMono = getImageClientResponse(Duration.ofSeconds(1));
        doReturn(imageResponseMono)
                .when(imageRetrieveService).fetchImage(fetchImageRequest);

        ProcessedImage processedImage = ProcessImage.getProcessingResult(imageResponseMono.block().body(), ProcessedImage.builder().imageUrl(fetchImageRequest.imageUrl));
        String storedBody = new ObjectMapper().writeValueAsString(processedImage);
        doReturn(createElasticPutResponse(Duration.ofSeconds(1), HttpStatus.CREATED, "123"))
                .when(elasticService).post(storedBody);

        CountDownLatch latch = new CountDownLatch(1);
        final WebTestClient threadClient = webClient;
        List<Thread> threads = new ArrayList();
        AtomicReference<Throwable> fail = new AtomicReference();
        AtomicInteger fails = new AtomicInteger(0);
        for (int i = 0; i < 50; i++) {
            Thread t = new Thread(() -> {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                try {

                    String bodyString = threadClient.mutate().responseTimeout(Duration.ofSeconds(600)).build()
                            .post()
                            .uri("/image")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .body(Mono.just("{\"imageUrl\": \"" + DUMMY_IMAGE_URL + "\"}"), String.class)
                            .exchange()
                            .expectStatus().isEqualTo(HttpStatus.CREATED)
                            .expectBody(String.class).returnResult().getResponseBody();
                    HashMap<String, Object> bodyMap =
                            new ObjectMapper().readValue(bodyString, HashMap.class);
                    assertThat(bodyMap.get("_id"), instanceOf(String.class));
                } catch (Throwable e) {
                    fail.set(e);
                    fails.incrementAndGet();
                }
            });
            t.start();
            threads.add(t);
        }
        long start = System.nanoTime();
        latch.countDown();
        for (Thread t : threads) {
            t.join();
        }
        long end = System.nanoTime();
        long timeTaken = TimeUnit.NANOSECONDS.toMillis(end - start);
        // this is a really dumb way to check of requests are actually processed in paralell or block. Find a better way later.
        assertThat(timeTaken, lessThan(5000l));
        assertThat(fail.get(), nullValue());
    }

    @Test
    public void testImageSearch() throws IOException {
        ImageRetrieveService.FetchImageRequest fetchImageRequest = new ImageRetrieveService.FetchImageRequest(DUMMY_IMAGE_URL);
        Mono<ImageRetrieveService.ImageFetchResponse> imageResponseMono = getImageClientResponse(Duration.ZERO);
        doReturn(imageResponseMono)
                .when(imageRetrieveService).fetchImage(fetchImageRequest);

        ProcessedImage processedImage = ProcessImage.getProcessingResult(imageResponseMono.block().body(), ProcessedImage.builder().imageUrl(fetchImageRequest.imageUrl));
        String queryBody = SearchImageHandler.generateQuery(processedImage, new SearchImageRequest(DUMMY_IMAGE_URL, 10));
        doReturn(createElasticSearchResponse(Duration.ZERO, HttpStatus.OK))
                .when(elasticService).search(queryBody);

        String bodyString = this.webClient
                .post()
                .uri("/image_search").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"imageUrl\": \"" + DUMMY_IMAGE_URL + "\", \"minimumShouldMatch\": 10}"), String.class)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody(String.class).returnResult().getResponseBody();
        assertThat(bodyString, equalTo("{ this is really irrelevant because we only pass on the elasticsearch response here }"));
    }

    @Test
    public void testImageResponseWithSearchRelayed() throws IOException {
        ImageRetrieveService.FetchImageRequest fetchImageRequest = new ImageRetrieveService.FetchImageRequest(DUMMY_IMAGE_URL);
        Mono<ImageRetrieveService.ImageFetchResponse> imageResponseMono = getImageClientResponse(Duration.ZERO, HttpStatus.NOT_FOUND);
        doReturn(imageResponseMono)
                .when(imageRetrieveService).fetchImage(fetchImageRequest);

        this.webClient
                .post()
                .uri("/image_search")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"imageUrl\": \"" + DUMMY_IMAGE_URL + "\"}"), String.class)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class).isEqualTo("{\"message\":\"Could not fetch image.\"}");
    }

    @Test
    public void testSearchImageWithFaultyUrl() {
        this.webClient
                .post()
                .uri("/image_search")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"image_url1\": \"https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg\"}"), String.class)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
                .expectBody().jsonPath("message").isEqualTo("imageUrl was not specified in request.");
    }

}