package kitchen.sink.member.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/** Verifies the access tokens issued by auth-service. This service never signs tokens, so it holds no private key. */
@Configuration
public class JwtConfig {

    private static final int MIN_MODULUS_BITS = 2048;

    /** Creates the decoder that checks signature, expiry, issuer and that the audience names this service. */
    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey(properties.publicKey()))
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(properties.audience()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), audience));
        return decoder;
    }

    /** Parses the X.509 PEM public key; the app refuses to start without a valid one. */
    static RSAPublicKey publicKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("app.security.jwt.public-key (the PEM contents in JWT_PUBLIC_KEY) must be set");
        }
        // environment variables are often set on one line, with a literal \n between the lines
        String text = pem.replace("\\n", "\n");
        String begin = "-----BEGIN PUBLIC KEY-----";
        String end = "-----END PUBLIC KEY-----";
        int from = text.indexOf(begin);
        int to = text.indexOf(end);
        if (from < 0 || to < from) {
            throw new IllegalStateException("The JWT key is not a PEM 'PUBLIC KEY' (X.509)");
        }
        try {
            byte[] der = Base64.getDecoder().decode(text.substring(from + begin.length(), to).replaceAll("\\s", ""));
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            if (key.getModulus().bitLength() < MIN_MODULUS_BITS) {
                throw new IllegalStateException("JWT RSA key must be at least " + MIN_MODULUS_BITS + " bits");
            }
            return key;
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException("Could not load the JWT public key: " + e.getMessage(), e);
        }
    }
}
