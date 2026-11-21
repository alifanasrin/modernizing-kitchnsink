package kitchen.sink.demo.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One authorization request on its way to a token. It has these phases in the same document:
 * <ol>
 *   <li><b>requested</b>: created by {@code GET /oauth2/authorize}, found by {@code requestId}, no member yet;</li>
 *   <li><b>issued</b>: after the password is accepted the member is attached and a short-lived authorization code is
 *       set (only its SHA-256 is stored) and {@code expiresAt} moves to the code expiry.</li>
 * </ol>
 * The code is single use: the token endpoint atomically moves the status from {@code CODE_ISSUED} to {@code USED}. A TTL index on {@code expiresAt}
 * removes abandoned documents. Never update it with a read-then-write: use the atomic operations in
 * {@code AuthorizationGrantStore}.
 */
@Document(collection = "oauth_authorization")
@Getter
@NoArgsConstructor
public class AuthorizationGrant {
    @Id
    private String id;

    /** Opaque id handed to the login page; null once the code is issued. */
    private String requestId;

    /** SHA-256 of the authorization code; null while pending. */
    private String codeHash;

    /** The Mongo {@code _id} (hex ObjectId) of the authenticated member; null before the password check. */
    private String userId;

    /** The session the code was exchanged for; set after the exchange as a record of the result; a replay of the code does not revoke it. */
    private String sessionId;

    private String clientId;
    private String redirectUri;

    /** The client's opaque {@code state} (RFC 6749 section 4.1.1), echoed in the redirect back to it; may be null. */
    private String state;

    /** PKCE code challenge (S256) the client committed to. */
    private String codeChallenge;

    /** Where the request is in its life; a grant only moves forward, so a used code can never be used again. */
    private Status status;

    private Instant createdAt;
    private Instant expiresAt;

    public enum Status {
        /** Created by the authorization endpoint, waiting for the password. */
        REQUESTED,
        /** Authorization code issued, waiting for the token exchange. */
        CODE_ISSUED,
        /** Code exchanged; kept (until its TTL) so a replay is refused. */
        USED
    }

    /** A request just validated at the authorization endpoint: no member yet. */
    public AuthorizationGrant(String requestId, String clientId, String redirectUri, String codeChallenge,
                              Instant now, Instant expiresAt) {
        this(requestId, clientId, redirectUri, codeChallenge, null, now, expiresAt);
    }

    /** As above, remembering the client's {@code state} so it can be echoed once the code (or an error) is sent. */
    public AuthorizationGrant(String requestId, String clientId, String redirectUri, String codeChallenge,
                              String state, Instant now, Instant expiresAt) {
        this.requestId = requestId;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.codeChallenge = codeChallenge;
        this.state = state;
        this.status = Status.REQUESTED;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }
}
