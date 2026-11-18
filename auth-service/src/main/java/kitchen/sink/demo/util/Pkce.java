package kitchen.sink.demo.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** PKCE (RFC 7636) with the S256 method, the only one OAuth 2.1 allows. */
public final class Pkce {

    /** The only accepted code challenge method. */
    public static final String S256 = "S256";

    private Pkce() {
    }

    /** Returns BASE64URL(SHA256(verifier)) without padding. */
    public static String challengeFor(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Tells whether the verifier belongs to the challenge, compared in constant time. */
    public static boolean matches(String verifier, String challenge) {
        return MessageDigest.isEqual(challengeFor(verifier).getBytes(StandardCharsets.US_ASCII),
                challenge.getBytes(StandardCharsets.US_ASCII));
    }
}
