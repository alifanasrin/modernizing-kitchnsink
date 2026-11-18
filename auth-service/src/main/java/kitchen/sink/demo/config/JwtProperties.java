package kitchen.sink.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Token settings bound from {@code app.security.jwt.*}. Access tokens are signed with RS256: the PKCS#8 PEM private
 * key and X.509 PEM public key are the <em>contents</em> of the environment variables {@code JWT_PRIVATE_KEY} /
 * {@code JWT_PUBLIC_KEY} (the whole PEM text, including the BEGIN/END lines; a literal {@code \n} stands for a line
 * break). Neither has a default value and the app refuses to start without a valid, matching pair (see
 * {@link JwtKeys}).
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String privateKey, String publicKey, String issuer, String audience,
                            Duration accessTokenTtl, Duration refreshTokenTtl) {

    public JwtProperties {
        issuer = issuer == null || issuer.isBlank() ? "http://localhost:8080/auth-service" : issuer;
        audience = audience == null || audience.isBlank() ? "http://localhost:8080/auth-service" : audience;
        accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(1) : refreshTokenTtl;
    }
}
