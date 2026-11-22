package kitchen.sink.demo.exception;

import org.springframework.http.HttpStatus;

/**
 * member-service could not create the member, so the registration is undone. Carries the HTTP status the register
 * API answers with (503 when member-service is unavailable or unreachable, 500 otherwise). The message is safe to
 * show to clients; the cause (which may hold member data) is only logged.
 */
public class MemberServiceException extends RuntimeException {

    private final HttpStatus status;

    public MemberServiceException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
