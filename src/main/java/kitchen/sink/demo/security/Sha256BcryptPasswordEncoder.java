package kitchen.sink.demo.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * BCrypt for passwords of any length. BCrypt only reads the first 72 bytes, so a long or non-ASCII
 * password would be silently truncated. Following the OWASP Password Storage Cheat Sheet the
 * password is first reduced to a fixed 44-character Base64 SHA-256 digest (no NUL bytes, well under 72), and that
 * digest is BCrypt-hashed. Nothing is trimmed or truncated.
 */
public class Sha256BcryptPasswordEncoder implements PasswordEncoder {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    /** Hashes the pre-hashed password with BCrypt. */
    @Override
    public String encode(CharSequence rawPassword) {
        return bcrypt.encode(preHash(rawPassword));
    }

    /** Checks the pre-hashed password against a BCrypt hash. */
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return bcrypt.matches(preHash(rawPassword), encodedPassword);
    }

    /** Returns the Base64 SHA-256 of the password. */
    static String preHash(CharSequence rawPassword) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
