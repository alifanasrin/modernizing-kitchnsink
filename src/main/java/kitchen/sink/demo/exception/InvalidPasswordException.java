package kitchen.sink.demo.exception;

import org.springframework.http.HttpStatus;

/** The password breaks the length rules; the message never contains the password. */
public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException(String message) {
        super(message);
    }
}
