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
import visualsearch.image.ProcessedImage;
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

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static visualsearch.service.HelperMethods.createElasticPutResponse;
import static visualsearch.service.HelperMethods.getImageClientResponse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doReturn;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HelloControllerTest {

    @Autowired
    private WebTestClient webClient;

    @MockBean
    private ElasticService elasticService;

    @MockBean
    private ImageRetrieveService imageRetrieveService;


    @Test
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
    public void testImage() throws IOException {
        ImageRetrieveService.FetchImageRequest fetchImageRequest = new ImageRetrieveService.FetchImageRequest("https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg");
        String storedBody = new ObjectMapper().writeValueAsString(ProcessedImage.builder().capacity(3).imageUrl(fetchImageRequest.imageUrl).build());
        doReturn(createElasticPutResponse(HttpStatus.CREATED))
                .when(elasticService).post(storedBody);
        doReturn(getImageClientResponse(Duration.ZERO))
                .when(imageRetrieveService).fetchImage(fetchImageRequest);
        String bodyString = this.webClient.mutate().responseTimeout(Duration.ofSeconds(600)).build().post().uri("/image").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"imageUrl\": \"https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg\"}"), String.class).exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody(String.class).returnResult().getResponseBody();
        HashMap<String, Object> bodyMap =
                new ObjectMapper().readValue(bodyString, HashMap.class);
        assertThat(bodyMap.get("_id"), instanceOf(String.class));
    }

    @Test
    public void testImageResponseRelayed() {
        ImageRetrieveService.FetchImageRequest fetchImageRequest = new ImageRetrieveService.FetchImageRequest("https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg");
        doReturn(getImageClientResponse(Duration.ZERO, HttpStatus.NOT_FOUND))
                .when(imageRetrieveService).fetchImage(fetchImageRequest);
        this.webClient.mutate().responseTimeout(Duration.ofSeconds(600)).build().post().uri("/image").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"imageUrl\": \"https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg\"}"), String.class).exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class).isEqualTo("{\"message\":\"fetching image returned error\"}");
    }

    @Test
    public void testImageWithFaultyUrl() {
        this.webClient.mutate().responseTimeout(Duration.ofSeconds(600)).build().post().uri("/image").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"image_url1\": \"https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg\"}"), String.class).exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
                .expectBody().jsonPath("message").isEqualTo("imageUrl was not specified in request");
    }

    @Test
    public void testImageParallel() throws InterruptedException, IOException {
        ImageRetrieveService.FetchImageRequest fetchImageRequest = new ImageRetrieveService.FetchImageRequest("https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg");
        String storedBody = new ObjectMapper().writeValueAsString(ProcessedImage.builder().capacity(3).imageUrl(fetchImageRequest.imageUrl).build());
        doReturn(createElasticPutResponse(HttpStatus.CREATED))
                .when(elasticService).post(storedBody);
        doReturn(getImageClientResponse(Duration.ofMillis(1000)))
                .when(imageRetrieveService).fetchImage(fetchImageRequest);
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

                    String bodyString = threadClient.mutate().responseTimeout(Duration.ofSeconds(600)).build().post().uri("/image").contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .body(Mono.just("{\"imageUrl\": \"https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg\"}"), String.class).exchange()
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
        System.out.println("num failures: " + fails.get());
        System.out.println(TimeUnit.NANOSECONDS.toMillis(end - start));
        if (fail.get() != null) {
            System.out.println(fail.get().toString());
        }
        assertThat(fail.get(), nullValue());
    }


}