package kitchen.sink.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token verification settings bound from {@code app.security.jwt.*}: the PEM contents of auth-service's public key,
 * the {@code iss} that tokens must carry and the {@code aud} value that must be in their audience list.
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String publicKey, String issuer, String audience) {
}
