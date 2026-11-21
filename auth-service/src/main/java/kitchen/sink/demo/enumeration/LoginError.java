package kitchen.sink.demo.enumeration;

import java.util.Arrays;
import java.util.Optional;

/** Why a sign-in attempt on the hosted login page failed: the {@code error} query code and the message shown to the user. */
public enum LoginError {
    INVALID("invalid", "Invalid email or password."),
    INVALID_INPUT("invalid_input", "Enter a valid email and a password of 15 to 64 characters."),
    EXPIRED("expired", "This sign-in request has expired. Please go back to the application and start again.");

    private final String code;
    private final String message;

    LoginError(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public static Optional<LoginError> fromCode(String code) {
        return Arrays.stream(values()).filter(e -> e.code.equals(code)).findFirst();
    }
}
