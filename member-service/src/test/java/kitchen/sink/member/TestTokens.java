package kitchen.sink.member;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/** Signs access tokens like auth-service does, with the throwaway test key pair. */
public final class TestTokens {

    public static final String ISSUER = "http://localhost:8080/auth-service";
    public static final String AUDIENCE = "member-service";

    private static final RSAPrivateKey PRIVATE = (RSAPrivateKey) key("private.pem", "PRIVATE KEY", true);
    private static final RSAPublicKey PUBLIC = (RSAPublicKey) key("public.pem", "PUBLIC KEY", false);

    private TestTokens() {
    }

    /** A valid token: right issuer, audience containing this service, 15 minutes to live. */
    public static String valid() {
        return token(ISSUER, List.of("other-api", AUDIENCE), Instant.now(), Duration.ofMinutes(15), PRIVATE, PUBLIC);
    }

    /** A token with the given issuer, audience and lifetime, signed with the trusted test key. */
    public static String token(String issuer, List<String> audience, Instant issuedAt, Duration ttl) {
        return token(issuer, audience, issuedAt, ttl, PRIVATE, PUBLIC);
    }

    /** A token that is valid in every way except that it is signed by another key pair. */
    public static String forged() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            return token(ISSUER, List.of(AUDIENCE), Instant.now(), Duration.ofMinutes(15),
                    (RSAPrivateKey) pair.getPrivate(), (RSAPublicKey) pair.getPublic());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String token(String issuer, List<String> audience, Instant issuedAt, Duration ttl,
                                RSAPrivateKey privateKey, RSAPublicKey publicKey) {
        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("ann@example.com")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .claim("sid", "sess1")
                .claim("azp", "spa");
        if (audience != null) {
            claims.audience(audience);
        }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
                .getTokenValue();
    }

    private static Object key(String file, String label, boolean isPrivate) {
        try {
            String pem = Files.readString(Path.of("src/test/resources/test-keys", file));
            String body = pem.replaceAll("-----(BEGIN|END) " + label + "-----", "").replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(body);
            KeyFactory rsa = KeyFactory.getInstance("RSA");
            return isPrivate ? rsa.generatePrivate(new PKCS8EncodedKeySpec(der))
                    : rsa.generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
