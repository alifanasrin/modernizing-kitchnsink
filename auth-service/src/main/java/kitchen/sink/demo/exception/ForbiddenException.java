package kitchen.sink.demo.exception;

import org.springframework.http.HttpStatus;

/** The caller is known but not allowed to do this. The message is safe to show to clients. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
