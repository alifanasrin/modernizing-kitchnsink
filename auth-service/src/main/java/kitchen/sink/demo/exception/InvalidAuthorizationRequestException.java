package kitchen.sink.demo.exception;


/** The authorization request is unknown, expired or used, or names an unknown client or an unregistered redirect URI. Safe to show to clients. */
public class InvalidAuthorizationRequestException extends RuntimeException {
    public InvalidAuthorizationRequestException(String message) {
        super(message);
    }
}
