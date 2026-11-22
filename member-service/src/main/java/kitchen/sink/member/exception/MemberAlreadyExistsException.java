package kitchen.sink.member.exception;

/** A member with the same email already exists. */
public class MemberAlreadyExistsException extends RuntimeException {
    public MemberAlreadyExistsException(String message) {
        super(message);
    }
}
