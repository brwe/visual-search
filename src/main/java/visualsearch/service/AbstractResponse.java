package visualsearch.service;

import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

public abstract class AbstractResponse {

    public abstract Mono<ServerResponse> getServerResponse();
}
