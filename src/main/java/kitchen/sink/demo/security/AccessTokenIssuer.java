package kitchen.sink.demo.security;

import java.time.Instant;
import java.util.List;

/** Issues the short-lived access token; services depend on this, not on the JWT implementation. */
public interface AccessTokenIssuer {

    /** Name of the claim that carries the session id. */
    String SESSION_CLAIM = "sid";

    /** Name of the claim that carries the authorized party: the {@code client_id} the token was issued to. */
    String AUTHORIZED_PARTY_CLAIM = "azp";

    /** Name of the claim that carries the client IP address the session was started from. */
    String IP_CLAIM = "ip";

    /**
     * Creates a signed access token for the email, session and client IP address. {@code audience} (the {@code aud}
     * list of the client) becomes the {@code aud} claim; without one the token has no {@code aud} and is refused by
     * the decoder. {@code clientId} becomes the {@code azp} claim. The token never outlives its session: it expires at the earlier of
     * {@code now + access token ttl} and {@code sessionExpiresAt}.
     */
    String issueAccessToken(String email, String sessionId, List<String> audience, String clientId,
                            String ipAddress, Instant now,
                            Instant sessionExpiresAt);

    /** Returns the lifetime in seconds of a token issued at {@code now}, capped at the life of the session. */
    long accessTokenTtlSeconds(Instant now, Instant sessionExpiresAt);
}
