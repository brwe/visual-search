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
import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.connection.waiting.HealthChecks;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
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
import visualsearch.service.services.ElasticService;
import visualsearch.service.services.ImageRetrieveService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;

import static io.netty.handler.codec.http.HttpHeaders.Values.APPLICATION_JSON;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.doReturn;
import static visualsearch.service.HelperMethods.getImageClientResponse;
import static visualsearch.service.services.ElasticService.ELASTIC_HOST;
import static visualsearch.service.services.ElasticService.ELASTIC_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ElasticStoreIntegTest {

    @Autowired
    private WebTestClient webClient;

    @MockBean
    private ImageRetrieveService imageRetrieveService;

    @BeforeClass
    public static void setElasticEnv() {
        System.setProperty(ELASTIC_HOST, "localhost");
        System.setProperty(ELASTIC_PORT, "9200");
    }

    @ClassRule
    public static DockerComposeRule docker = DockerComposeRule.builder()
            .file("src/test/resources/docker-compose-elasticsearch.yml")
            .waitingForService("elasticsearch", HealthChecks.toHaveAllPortsOpen())
            .build();

    @Before
    public void wipeCluster() throws IOException {
        HttpHost httpHost = new HttpHost(System.getProperty(ELASTIC_HOST), Integer.parseInt(System.getProperty(ELASTIC_PORT)));
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpDelete refreshRequest = new HttpDelete(httpHost.toURI() + "/images");
            refreshRequest.addHeader("accept", APPLICATION_JSON);
            try (CloseableHttpResponse response = client.execute(refreshRequest)) {
                assertThat(response.getStatusLine().getStatusCode(), anyOf(equalTo(HttpStatus.OK.value()), equalTo(HttpStatus.NOT_FOUND.value())));
            }
        }
    }

    @Test
    public void testElasticServiceWorks() throws IOException {

        try (ElasticService elasticService = new ElasticService()) {
            ElasticService.ElasticResponse elasticResponse = elasticService.post("{}").block();
            assertThat(elasticResponse.getHttpStatus(), equalTo(HttpStatus.CREATED));
        }
    }

    @Test
    public void testImage() throws IOException {
        ImageRetrieveService.FetchImageRequest fetchImageRequest = new ImageRetrieveService.FetchImageRequest("https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg");
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

        // now check that all was actually stored
        HttpHost httpHost = new HttpHost(System.getProperty(ELASTIC_HOST), Integer.parseInt(System.getProperty(ELASTIC_PORT)));
        try (CloseableHttpClient client = HttpClients.createDefault();) {

            HttpPost request = new HttpPost(httpHost.toURI() + "/_refresh");
            request.addHeader("accept", APPLICATION_JSON);
            try (CloseableHttpResponse response = client.execute(request)) {

            }

            HttpGet searchRequest = new HttpGet(httpHost.toURI() + "/images/_search");
            request.addHeader("accept", APPLICATION_JSON);
            try (CloseableHttpResponse response = client.execute(searchRequest)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                HashMap<String, Object> result =
                        new ObjectMapper().readValue(responseBody, HashMap.class);
                assertThat(((HashMap<String, Object>) result.get("hits")).get("total"), equalTo(1));
                ArrayList<Object> hits = (ArrayList<Object>) ((HashMap<String, Object>) result.get("hits")).get("hits");
                HashMap<String, Object> hit = (HashMap<String, Object>) hits.get(0);
                HashMap<String, Object> source = (HashMap<String, Object>) hit.get("_source");
                assertThat(source.get("imageUrl"), equalTo("https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg"));
                assertThat(source.get("receivedBytes"), equalTo(3));
            }

        }

    }

    @Test
    public void testSearchImage() throws IOException {
        String imageUrl = "https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg";
        ImageRetrieveService.FetchImageRequest fetchImageRequest = new ImageRetrieveService.FetchImageRequest("https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg");
        doReturn(getImageClientResponse(Duration.ZERO))
                .when(imageRetrieveService).fetchImage(fetchImageRequest);

        // index a few images that are similar to the searched image
        HttpHost httpHost = new HttpHost(System.getProperty(ELASTIC_HOST), Integer.parseInt(System.getProperty(ELASTIC_PORT)));
        try (CloseableHttpClient client = HttpClients.createDefault()) {

            for (int i = 0; i < 5; i++) {
                HttpPost indexRequest = new HttpPost(httpHost.toURI() + "/" + ElasticService.INDEX + "/" + ElasticService.TYPE);
                indexRequest.addHeader("accept", APPLICATION_JSON);
                BasicHttpEntity entity = new BasicHttpEntity();
                int receivedBytes = 1 + i * 3;
                entity.setContent(new ByteArrayInputStream(("{\"imageUrl\" : \"" + imageUrl + "\", \"receivedBytes\": " + receivedBytes + "}").getBytes()));
                indexRequest.setEntity(entity);
                try (CloseableHttpResponse response = client.execute(indexRequest)) {
                    assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.CREATED.value()));
                }
            }


            HttpPost refreshRequest = new HttpPost(httpHost.toURI() + "/_refresh");
            refreshRequest.addHeader("accept", APPLICATION_JSON);
            try (CloseableHttpResponse response = client.execute(refreshRequest)) {
                assertThat(response.getStatusLine().getStatusCode(), equalTo(HttpStatus.OK.value()));
            }
        }

        String bodyString = this.webClient.mutate().responseTimeout(Duration.ofSeconds(600)).build().post().uri("/image_search").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"imageUrl\": \"" + imageUrl + "\"}"), String.class).exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody(String.class).returnResult().getResponseBody();

        HashMap<String, Object> result =
                new ObjectMapper().readValue(bodyString, HashMap.class);
        assertThat(((HashMap<String, Object>) result.get("hits")).get("total"), equalTo(5));
        ArrayList<Object> hits = (ArrayList<Object>) ((HashMap<String, Object>) result.get("hits")).get("hits");
        HashMap<String, Object> hit = (HashMap<String, Object>) hits.get(0);
        HashMap<String, Object> source = (HashMap<String, Object>) hit.get("_source");
        assertThat(source.get("receivedBytes"), equalTo(4));
        hit = (HashMap<String, Object>) hits.get(1);
        source = (HashMap<String, Object>) hit.get("_source");
        assertThat(source.get("receivedBytes"), equalTo(1));
        hit = (HashMap<String, Object>) hits.get(2);
        source = (HashMap<String, Object>) hit.get("_source");
        assertThat(source.get("receivedBytes"), equalTo(7));
        hit = (HashMap<String, Object>) hits.get(3);
        source = (HashMap<String, Object>) hit.get("_source");
        assertThat(source.get("receivedBytes"), equalTo(10));
        hit = (HashMap<String, Object>) hits.get(4);
        source = (HashMap<String, Object>) hit.get("_source");
        assertThat(source.get("receivedBytes"), equalTo(13));
    }
}