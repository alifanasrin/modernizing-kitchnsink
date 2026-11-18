package kitchen.sink.demo.security;

import kitchen.sink.demo.exception.InvalidPasswordException;
import org.springframework.stereotype.Component;

/**
 * Length rules for new passwords, per the password guidelines of NIST SP 800-63B and OWASP: a minimum of 15 and a maximum of 64 (NIST: at least 15 when the password is the only factor, and accept at least 64), and no composition rules. Every character is
 * allowed (spaces, Unicode, symbols); the value is never trimmed or truncated, not normalized in any way either: it is measured and
 * hashed exactly as typed. The limits are fixed constants, not configuration, so they cannot differ between environments. Pasting and password managers are not restricted
 * by anything on the server side.
 */
@Component
public class PasswordPolicy {

    /** Minimum length in code points; the same in every environment. */
    public static final int MIN_LENGTH = 15;
    /** Maximum length in code points; bounds hashing cost. The same in every environment. */
    public static final int MAX_LENGTH = 64;

    /** Returns the minimum allowed password length. */
    public int minLength() {
        return MIN_LENGTH;
    }

    /** Returns the maximum allowed password length. */
    public int maxLength() {
        return MAX_LENGTH;
    }

    /** Throws if the password is shorter or longer than allowed. */
    public void validate(String password) {
        if (password == null) {
            throw new InvalidPasswordException("Password is mandatory");
        }
        int length = length(password);
        if (length < minLength()) {
            throw new InvalidPasswordException("Password must be at least " + minLength() + " characters");
        }
        if (length > maxLength()) {
            throw new InvalidPasswordException("Password must be at most " + maxLength() + " characters");
        }
    }

    /** Tells whether the password is longer than the maximum allowed length. */
    public boolean exceedsMaxLength(String password) {
        return password != null && length(password) > maxLength();
    }

    /** Returns the number of Unicode code points, so an emoji counts once. */
    private static int length(String password) {
        return password.codePointCount(0, password.length());
    }
}
