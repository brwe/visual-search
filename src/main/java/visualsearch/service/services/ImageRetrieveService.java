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

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.util.EntityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.springframework.http.MediaType.IMAGE_JPEG_VALUE;

@Service
public class ImageRetrieveService implements AutoCloseable {

    // cannot use this client probably because of https://github.com/reactor/reactor-netty/issues/119
    // check in again in a few weeks?
    // or maybe it is because I did not release the buffer. Look at Jackson2Tokenizer to see how this goes
    // private WebClient client = WebClient.create();

    private final CloseableHttpAsyncClient client = HttpAsyncClients.createDefault();

    public ImageRetrieveService() {
        client.start();
    }


    public Mono<ImageRetrieveService.ImageResponse> fetchImage(FetchImageRequest request) {

        return Mono.<ImageResponse>create(sink -> {
            FutureCallback<HttpResponse> callback = new FutureCallback<HttpResponse>() {

                @Override
                public void completed(HttpResponse result) {

                    try {
                        sink.success(new ImageResponse(ByteBuffer.wrap(EntityUtils.toByteArray(result.getEntity())), HttpStatus.resolve(result.getStatusLine().getStatusCode()), request.imageUrl));
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
            HttpGet getRequest = new HttpGet(request.imageUrl);
            getRequest.addHeader("accept", IMAGE_JPEG_VALUE);
            client.execute(getRequest, callback);

        });
    }

    public static class ImageResponse {

        private final ByteBuffer body;
        private final HttpStatus statusCode;
        private final String imageUrl;

        public ImageResponse(ByteBuffer body, HttpStatus statusCode, String imageUrl) {

            this.body = body;
            this.statusCode = statusCode;
            this.imageUrl = imageUrl;
        }

        public ByteBuffer body() {
            return body;
        }

        public HttpStatus statusCode() {
            return statusCode;
        }
        public String imageUrl() {
            return imageUrl;
        }
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

    public static class FetchImageRequest {
        public String getImageUrl() {
            return imageUrl;
        }

        public final String imageUrl;

        public FetchImageRequest(String imageUrl) {

            this.imageUrl = imageUrl;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FetchImageRequest that = (FetchImageRequest) o;

            return imageUrl != null ? imageUrl.equals(that.imageUrl) : that.imageUrl == null;
        }

        @Override
        public int hashCode() {
            return imageUrl != null ? imageUrl.hashCode() : 0;
        }
    }
}