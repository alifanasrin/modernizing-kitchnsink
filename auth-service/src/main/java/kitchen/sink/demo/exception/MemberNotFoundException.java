package kitchen.sink.demo.exception;

/** No member with the requested email. */
public class MemberNotFoundException extends RuntimeException {
    public MemberNotFoundException(String message) {
        super(message);
    }
}
