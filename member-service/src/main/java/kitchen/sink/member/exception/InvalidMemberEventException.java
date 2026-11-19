package kitchen.sink.member.exception;

/** A member event that can never be processed (malformed, invalid or unsupported). Never retried: it goes to the DLT. */
public class InvalidMemberEventException extends RuntimeException {

    /** The message must not contain event values (PII). */
    public InvalidMemberEventException(String message) {
        super(message);
    }

    public InvalidMemberEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
