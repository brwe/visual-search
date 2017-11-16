package visualsearch.service;

import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

public class ResponsePublisher<T> {
    public final Mono<T> resultMono;
    public final Class<T> responseClass;
    public final HttpStatus status;

    public ResponsePublisher(Mono<T> resultMono, Class<T> responseClass, HttpStatus status) {

        this.resultMono = resultMono;
        this.responseClass = responseClass;
        this.status = status;
    }
}
