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
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.web.reactive.function.BodyExtractor;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static org.springframework.http.MediaType.APPLICATION_JSON;

public abstract class Handler<Request, Response> {

    final Logger logger = LoggerFactory.getLogger(this.getClass());
    final ImageRetrieveService imageRetrieveService;
    final ElasticService elasticService;

    final BodyExtractor<Mono<Request>, ReactiveHttpInputMessage> requestExtractor;

    public Handler(ImageRetrieveService imageRetrieveService, ElasticService elasticService, Class<Request> requestClass) {
        requestExtractor = BodyExtractors.toMono(requestClass);
        this.imageRetrieveService = imageRetrieveService;
        this.elasticService = elasticService;
    }

    public Mono<ServerResponse> handle(ServerRequest request) {
        Mono<Request> indexImageRequestMono = request.body(requestExtractor);
        Mono<ResponsePublisher> responsePublisherMono = computeResponse(indexImageRequestMono);
        return responsePublisherMono.flatMap(responsePublisher -> ServerResponse
                .status(responsePublisher.status)
                .contentType(APPLICATION_JSON)
                .body(responsePublisher.resultMono, responsePublisher.responseClass));
    }

    abstract Mono<ResponsePublisher> computeResponse(Mono<Request> indexImageRequestMono);

    public static class ErrorMessage {
        public String message;

        public ErrorMessage(String message) {
            this.message = message;
        }
    }
}