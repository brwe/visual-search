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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.web.reactive.function.BodyExtractor;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import visualsearch.image.ProcessImage;
import visualsearch.image.ProcessedImage;
import visualsearch.service.search.SearchImageHandler;
import visualsearch.service.services.ElasticService;
import visualsearch.service.services.ImageRetrieveService;

import java.io.IOException;

import static org.springframework.http.MediaType.APPLICATION_JSON;

public abstract class Handler<Request, Response extends AbstractResponse> {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    protected final ImageRetrieveService imageRetrieveService;
    protected final ElasticService elasticService;

    final BodyExtractor<Mono<Request>, ReactiveHttpInputMessage> requestExtractor;

    public Handler(ImageRetrieveService imageRetrieveService, ElasticService elasticService, Class<Request> requestClass) {
        requestExtractor = BodyExtractors.toMono(requestClass);
        this.imageRetrieveService = imageRetrieveService;
        this.elasticService = elasticService;
    }

    protected static Mono<ServerResponse> handleError(Throwable t) {
        if (t instanceof Handler.RequestFailedException) {
            RequestFailedException requestFailedException = (RequestFailedException) t;
            return ServerResponse
                    .status(requestFailedException.getHttpStatus())
                    .contentType(APPLICATION_JSON)
                    .body(Mono.just(new ErrorMessage(requestFailedException.getMessage())), ErrorMessage.class);
        } else {
            return ServerResponse
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(APPLICATION_JSON)
                    .body(Mono.just(new ErrorMessage(t.toString())), ErrorMessage.class);
        }
    }

    protected Mono<ImageRetrieveService.ImageFetchResponse> fetchImage(String imageUrl) {
        if (imageUrl == null) {
            throw new SearchImageHandler.RequestFailedException(HttpStatus.BAD_REQUEST, "imageUrl was not specified in request.");
        } else {
            return imageRetrieveService.fetchImage(new ImageRetrieveService.FetchImageRequest(imageUrl));
        }
    }

    protected static ProcessedImage processImage(ImageRetrieveService.ImageFetchResponse imageResponse) {
        if (imageResponse.statusCode() != HttpStatus.OK) {
            throw new SearchImageHandler.RequestFailedException(imageResponse.statusCode(), "Could not fetch image.");
        } else {
            ProcessedImage.Builder resultBuilder = ProcessedImage.builder();
            resultBuilder.imageUrl(imageResponse.imageUrl());
            return processImage(imageResponse, resultBuilder);
        }
    }

    protected static ProcessedImage processImage(ImageRetrieveService.ImageFetchResponse imageResponse, ProcessedImage.Builder resultBuilder) {
        try {
            return ProcessImage.getProcessingResult(imageResponse.body(), resultBuilder);
        } catch (IOException e) {
            throw new SearchImageHandler.RequestFailedException(HttpStatus.INTERNAL_SERVER_ERROR, e, "Could not process image: ");
        }
    }

    protected static String getElasticsearchResponse(ElasticService.ElasticResponse elasticResponse) {
        try {
            return elasticResponse.getBody();
        } catch (IOException e) {
            throw new SearchImageHandler.RequestFailedException(HttpStatus.INTERNAL_SERVER_ERROR, e, "Could not extract response from elasticsearch: ");
        }
    }


    public Mono<ServerResponse> handle(ServerRequest request) {
        Mono<Request> indexImageRequestMono = request.body(requestExtractor);
        return computeResponse(indexImageRequestMono)
                .flatMap(response -> response.getServerResponse())
                .onErrorResume(t -> handleError(t));
    }

    protected abstract Mono<Response> computeResponse(Mono<Request> indexImageRequestMono);

    public static class ErrorMessage {
        public String message;

        public ErrorMessage(String message) {
            this.message = message;
        }
    }

    public static class RequestFailedException extends RuntimeException {
        private final HttpStatus httpStatus;
        private final Throwable t;
        private final String message;

        public RequestFailedException(HttpStatus httpStatus, Throwable t, String message) {
            this.t = t;
            this.httpStatus = httpStatus;
            this.message = message;
        }

        public RequestFailedException(HttpStatus httpStatus, String message) {
            this.t = null;
            this.httpStatus = httpStatus;
            this.message = message;
        }

        public HttpStatus getHttpStatus() {
            return httpStatus;
        }

        public String getMessage() {
            return message + (t == null ? "" : t.toString());
        }
    }
}