package kitchen.sink.demo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.util.List;

/** Signing and verifying access tokens (RS256). The filter chain that uses them is in {@link SecurityConfig}. */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    /** Loads the RSA key pair used to sign and verify access tokens. */
    @Bean
    JwtKeys jwtKeys(JwtProperties properties) {
        return JwtKeys.load(properties);
    }

    /** Creates the JWT encoder that signs tokens with the private key. */
    @Bean
    JwtEncoder jwtEncoder(JwtKeys keys) {
        return new NimbusJwtEncoder(keys.jwkSource());
    }

    /** Creates the JWT decoder that verifies signature, expiry and issuer with the public key. */
    @Bean  //todo::hey check this
    JwtDecoder jwtDecoder(JwtKeys keys, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        // default validators (exp/nbf) plus the issuer check, and the token's audience must contain this service
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(properties.audience()));
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), audience);
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
