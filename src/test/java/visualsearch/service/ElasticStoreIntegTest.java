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
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
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

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;

import static visualsearch.service.ElasticService.ELASTIC_HOST;
import static visualsearch.service.ElasticService.ELASTIC_PORT;
import static visualsearch.service.HelperMethods.getImageClientResponse;
import static io.netty.handler.codec.http.HttpHeaders.Values.APPLICATION_JSON;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.doReturn;

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

    @Test
    public void testElasticServiceWorks() throws IOException {

        try (ElasticService elasticService = new ElasticService()) {
            ElasticService.ElasticResponse elasticResponse = elasticService.post("{}").block();
            assertThat(elasticResponse.getHttpStatus(), equalTo(HttpStatus.CREATED));
        }
    }

    @Test
    public void testImage() throws IOException {
        IndexImageHandler.IndexImageRequest indexImageRequest = new IndexImageHandler.IndexImageRequest();
        indexImageRequest.imageUrl = "https://c7.staticflickr.com/6/5499/10245691204_98dce75b5a_o.jpg";
        doReturn(getImageClientResponse(Duration.ZERO))
                .when(imageRetrieveService).getImage(indexImageRequest);

        String bodyString = this.webClient.mutate().responseTimeout(Duration.ofSeconds(600)).build().post().uri("/visualsearch/image").contentType(MediaType.APPLICATION_JSON)
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
}