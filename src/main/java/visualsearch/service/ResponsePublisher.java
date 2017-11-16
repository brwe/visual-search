package visualsearch.service;

import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

public class ResponsePublisher<T> {
    Mono<T> resultMono;
    Class<T> responseClass;
    HttpStatus status;

    public ResponsePublisher(Mono<T> resultMono, Class<T> responseClass, HttpStatus status) {

        this.resultMono = resultMono;
        this.responseClass = responseClass;
        this.status = status;
    }
}
