package kitchen.sink.demo.exception;

import org.springframework.http.HttpStatus;

/** Wrong credentials, or an invalid/expired/reused token. The message is safe to show to clients. */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) {
        super( message);
    }
}
