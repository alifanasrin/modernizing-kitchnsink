package kitchen.sink.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** The RSA key pair used to sign (private) and verify (public) access tokens. Checked once at startup. */
public final class JwtKeys {

    private static final Logger log = LoggerFactory.getLogger(JwtKeys.class);

    static final int MIN_MODULUS_BITS = 2048;

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    public JwtKeys(RSAPrivateKey privateKey, RSAPublicKey publicKey) {
        if (publicKey.getModulus().bitLength() < MIN_MODULUS_BITS) {
            throw new IllegalStateException("JWT RSA key must be at least " + MIN_MODULUS_BITS + " bits");
        }
        if (privateKey instanceof RSAPrivateCrtKey crt
                && (!crt.getModulus().equals(publicKey.getModulus())
                || !crt.getPublicExponent().equals(publicKey.getPublicExponent()))) {
            throw new IllegalStateException("JWT private key and public key are not a pair");
        }
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    /** Reads and validates the PEM key pair named in the JWT properties. */
    public static JwtKeys load(JwtProperties properties) {
        String privatePem = properties.privateKey();
        String publicPem = properties.publicKey();
        if (privatePem == null || privatePem.isBlank() || publicPem == null || publicPem.isBlank()) {
            throw new IllegalStateException(
                    "app.security.jwt.private-key and public-key (the PEM contents in JWT_PRIVATE_KEY / JWT_PUBLIC_KEY) must be set");
        }
        try {
            byte[] privateDer = pem(privatePem, "PRIVATE KEY");
            byte[] publicDer = pem(publicPem, "PUBLIC KEY");
            KeyFactory rsa = KeyFactory.getInstance("RSA");
            JwtKeys keys = new JwtKeys((RSAPrivateKey) rsa.generatePrivate(new PKCS8EncodedKeySpec(privateDer)),
                    (RSAPublicKey) rsa.generatePublic(new X509EncodedKeySpec(publicDer)));
            log.info("JWT RSA key pair loaded ({} bits)", keys.publicKey.getModulus().bitLength());
            return keys;
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            log.error("could not load the JWT RSA key pair: {}", e.getMessage());
            throw new IllegalStateException("Could not load the JWT RSA key pair: " + e.getMessage(), e);
        }
    }

    /** Returns the public key used to verify access tokens. */
    public RSAPublicKey publicKey() {
        return publicKey;
    }

    /** Returns a key set holding the signing key for the JWT encoder. */
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        return new ImmutableJWKSet<>(new JWKSet(jwk));
    }

    /** Returns the private key bytes used to derive other secret keys. */
    public byte[] derivationSecret() {
        return privateKey.getEncoded();
    }

    /** Decodes the Base64 body of the PEM block with the given label. */
    private static byte[] pem(String value, String label) {
        // environment variables are often set on one line, with a literal \n between the lines
        String text = value.replace("\\n", "\n");
        String begin = "-----BEGIN " + label + "-----";
        String end = "-----END " + label + "-----";
        int from = text.indexOf(begin);
        int to = text.indexOf(end);
        if (from < 0 || to < from) {
            throw new IllegalStateException("The JWT key is not a PEM '" + label
                    + "' (private keys must be PKCS#8: 'BEGIN PRIVATE KEY')");
        }
        String body = text.substring(from + begin.length(), to).replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
