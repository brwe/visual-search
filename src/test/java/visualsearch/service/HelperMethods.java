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

import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import visualsearch.service.services.ElasticService;
import visualsearch.service.services.ImageRetrieveService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;

public class HelperMethods {

    public static final String DUMMY_IMAGE_URL = "http://iwishidlearnedsomethingotherthanprogramming.com";

    public static Mono<ImageRetrieveService.ImageFetchResponse> getImageClientResponse(Duration duration, HttpStatus httpStatus) throws IOException {
        ByteBuffer byteBuffer;
        try (FileInputStream fileInputStream = new FileInputStream(new File("src/test/resources/nginx/data/test.jpg"))) {
            byte[] imageBytes = IOUtils.toByteArray(fileInputStream);
            byteBuffer = ByteBuffer.wrap(imageBytes);
        }
        ImageRetrieveService.ImageFetchResponse imageResponse = new ImageRetrieveService.ImageFetchResponse(byteBuffer, httpStatus, DUMMY_IMAGE_URL);
        return Mono.just(imageResponse).delayElement(duration);
    }

    public static Mono<ImageRetrieveService.ImageFetchResponse> getImageClientRequest(Duration duration, HttpStatus httpStatus) throws IOException {
        ByteBuffer byteBuffer;
        try (FileInputStream fileInputStream = new FileInputStream(new File("src/test/resources/nginx/data/test.jpg"))) {
            byte[] imageBytes = IOUtils.toByteArray(fileInputStream);
            byteBuffer = ByteBuffer.wrap(imageBytes);
        }
        ImageRetrieveService.ImageFetchResponse imageResponse = new ImageRetrieveService.ImageFetchResponse(byteBuffer, httpStatus, DUMMY_IMAGE_URL);
        return Mono.just(imageResponse).delayElement(duration);
    }

    public static Mono<ImageRetrieveService.ImageFetchResponse> getImageClientResponse(Duration duration) throws IOException {
        return getImageClientResponse(duration, HttpStatus.OK);
    }

    public static Mono<ElasticService.ElasticResponse> createElasticPutResponse(Duration duration, HttpStatus httpStatus, String id) throws IOException {
        String body = "{\"_index\":\"images\",\"_type\":\"processed_images\",\"_id\":\"" + id + "\",\"_version\":1,\"result\":\"created\"}";
        ElasticService.ElasticResponse elasticResponse = new ElasticService.ElasticResponse(body, httpStatus.value());

        return Mono.just(elasticResponse).delayElement(duration);
    }

    public static Mono<ElasticService.ElasticResponse> createElasticSearchResponse(Duration duration, HttpStatus httpStatus) throws IOException {
        String body = "{ this is really irrelevant because we only pass on the elasticsearch response here }";
        ElasticService.ElasticResponse elasticResponse = new ElasticService.ElasticResponse(body, httpStatus.value());

        return Mono.just(elasticResponse).delayElement(duration);
    }

    public static Mono<ElasticService.ElasticResponse> createElasticPutResponse(HttpStatus httpStatus) throws IOException {
        return createElasticPutResponse(Duration.ZERO, httpStatus, "123");
    }

    public static byte[] getTestImageBytes() throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(new File("src/test/resources/nginx/data/test.jpg"))) {
            return IOUtils.toByteArray(fileInputStream);
        }
    }
}
