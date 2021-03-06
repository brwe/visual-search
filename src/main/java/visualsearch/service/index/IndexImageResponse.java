/*
 * Copyright 2017 a2tirb
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package visualsearch.service.index;

import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import visualsearch.service.AbstractResponse;

import static org.springframework.http.MediaType.APPLICATION_JSON;

public class IndexImageResponse extends AbstractResponse{
    public String _id;

    public Mono<ServerResponse> getServerResponse() {
        return ServerResponse
                .status(HttpStatus.CREATED)
                .contentType(APPLICATION_JSON)
                .body(Mono.just(this), IndexImageResponse.class);
    }
}
