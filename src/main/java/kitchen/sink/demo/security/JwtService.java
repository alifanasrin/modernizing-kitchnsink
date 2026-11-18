package kitchen.sink.demo.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kitchen.sink.demo.config.JwtProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Issues short-lived RS256 access tokens: {@code aud} = the client's aud list, {@code azp} = the client id, {@code sub} = the member's email, {@code sid} = session id. */
@Component
public class JwtService implements AccessTokenIssuer {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public JwtService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    /** Signs an RS256 access token carrying the email, session id and expiry. */
    @Override
    public String issueAccessToken(String email, String sessionId, List<String> audience,
                                   String clientId, String ipAddress, Instant now, Instant sessionExpiresAt) {
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(email)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTokenTtlSeconds(now, sessionExpiresAt)))
                .id(UUID.randomUUID().toString())
                .claim(SESSION_CLAIM, sessionId)
                .claim(AUTHORIZED_PARTY_CLAIM, clientId)
                .claim(IP_CLAIM, ipAddress == null ? "" : ipAddress);
        if (audience != null && !audience.isEmpty()) {
            builder.audience(audience);
        }
        JwtClaimsSet claims = builder.build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
        log.debug("access token issued for session {} (audience {}, azp {})", sessionId, audience, clientId);
        return token;
    }

    /** Returns the configured access token lifetime in seconds, cut short so it ends with the session. */
    @Override
    public long accessTokenTtlSeconds(Instant now, Instant sessionExpiresAt) {
        long ttl = properties.accessTokenTtl().toSeconds();
        if (sessionExpiresAt == null) {
            return ttl;
        }
        return Math.max(0, Math.min(ttl, Duration.between(now, sessionExpiresAt).toSeconds()));
    }
}
