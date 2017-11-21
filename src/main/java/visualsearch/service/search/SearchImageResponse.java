package visualsearch.service.search;

import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import visualsearch.service.AbstractResponse;

import static org.springframework.http.MediaType.APPLICATION_JSON;

public class SearchImageResponse extends AbstractResponse {

    public String response;

    public Mono<ServerResponse> getServerResponse() {
        return ServerResponse
                .status(HttpStatus.OK)
                .contentType(APPLICATION_JSON)
                .body(Mono.just(response), String.class);
    }
}
