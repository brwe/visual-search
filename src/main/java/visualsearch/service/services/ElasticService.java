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

package visualsearch.service.services;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;

import static io.netty.handler.codec.http.HttpHeaders.Values.APPLICATION_JSON;

@Service
public class ElasticService implements Closeable {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    public static final String ELASTIC_HOST = "ELASTIC_HOST";
    public static final String ELASTIC_PORT = "ELASTIC_PORT";
    public static String INDEX = "images";
    public static String TYPE = "processed_images";

    private final HttpHost httpHost;

    private final CloseableHttpAsyncClient client = HttpAsyncClients.createDefault();

    public ElasticService() {
        httpHost = new HttpHost(System.getProperty(ELASTIC_HOST), Integer.parseInt(System.getProperty(ELASTIC_PORT)));
        client.start();
        logger.info("Using elastic host " + System.getProperty(ELASTIC_HOST));
        logger.info("Using elastic port " + System.getProperty(ELASTIC_PORT));
    }

    public Mono<ElasticResponse> post(String body) {
        return Mono.<ElasticResponse>create(sink -> {
            FutureCallback<HttpResponse> callback = new FutureCallback<HttpResponse>() {

                @Override
                public void completed(HttpResponse result) {

                    try {
                        sink.success(new ElasticResponse(EntityUtils.toString(result.getEntity()), result.getStatusLine().getStatusCode()));
                    } catch (IOException e) {
                        sink.error(e);
                    }
                }

                @Override
                public void failed(Exception ex) {
                    sink.error(ex);
                }

                @Override
                public void cancelled() {
                    sink.error(new Exception("request was cancelled"));
                }


            };
            logger.debug("sending  " + body + " to elastic");
            HttpPost request = new HttpPost(httpHost.toURI() + "/" + INDEX + "/" + TYPE);
            BasicHttpEntity entity = new BasicHttpEntity();
            entity.setContent(new ByteArrayInputStream(body.getBytes()));
            request.setEntity(entity);
            request.addHeader("accept", APPLICATION_JSON);
            client.execute(request, callback);

        });
    }

    public Mono<ElasticResponse> search(String queryBody) {
        return Mono.<ElasticResponse>create(sink -> {
            FutureCallback<HttpResponse> callback = new FutureCallback<HttpResponse>() {

                @Override
                public void completed(HttpResponse result) {

                    try {
                        sink.success(new ElasticResponse(EntityUtils.toString(result.getEntity()), result.getStatusLine().getStatusCode()));
                    } catch (IOException e) {
                        sink.error(e);
                    }
                }

                @Override
                public void failed(Exception ex) {
                    sink.error(ex);
                }

                @Override
                public void cancelled() {
                    sink.error(new Exception("request was cancelled"));
                }


            };
            logger.debug("sending  query " + queryBody + " to elastic");
            HttpPost request = new HttpPost(httpHost.toURI() + "/" + INDEX + "/" + TYPE + "/_search");
            BasicHttpEntity entity = new BasicHttpEntity();
            entity.setContent(new ByteArrayInputStream(queryBody.getBytes()));
            request.setEntity(entity);
            request.addHeader("accept", APPLICATION_JSON);
            client.execute(request, callback);

        });
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        try {
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static class ElasticResponse {
        HttpStatus status;
        String body;

        public ElasticResponse(String body, int status) {
            this.body = body;
            this.status = HttpStatus.resolve(status);
        }

        public String getBody() throws IOException {
            return body;
        }

        public HttpStatus getHttpStatus() {
            return status;
        }
    }

}
