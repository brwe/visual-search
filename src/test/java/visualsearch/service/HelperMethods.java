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

import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;

public class HelperMethods {
    public static Mono<ImageRetrieveService.ImageResponse> getImageClientResponse(Duration duration, HttpStatus httpStatus) {

        ByteBuffer byteBuffer = ByteBuffer.wrap("abc".getBytes());
        ImageRetrieveService.ImageResponse imageResponse = new ImageRetrieveService.ImageResponse(byteBuffer, httpStatus);
        return Mono.just(imageResponse).delayElement(duration);
    }

    public static Mono<ImageRetrieveService.ImageResponse> getImageClientResponse(Duration duration) {
        return getImageClientResponse(duration, HttpStatus.OK);
    }

    public static Mono<ElasticService.ElasticResponse> createElasticPutResponse(Duration duration, HttpStatus httpStatus, String id) throws IOException {
        String body = "{\"_index\":\"images\",\"_type\":\"processed_images\",\"_id\":\"" + id + "\",\"_version\":1,\"result\":\"created\"}";
        ElasticService.ElasticResponse elasticResponse = new ElasticService.ElasticResponse(body, httpStatus.value());

        return Mono.just(elasticResponse).delayElement(duration);
    }

    public static Mono<ElasticService.ElasticResponse> createElasticPutResponse(HttpStatus httpStatus) throws IOException {
        return createElasticPutResponse(Duration.ZERO, httpStatus, "123");
    }
}
