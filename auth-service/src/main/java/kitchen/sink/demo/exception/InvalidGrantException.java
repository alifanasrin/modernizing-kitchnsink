package kitchen.sink.demo.exception;


/**
 * The token request is refused: unknown, expired or used code, wrong client or redirect URI, or a failed PKCE check.
 * One message for all of them so a caller cannot tell which part was wrong.
 */
public class InvalidGrantException extends RuntimeException {
    public InvalidGrantException(String message) {
        super(message);
    }
}
