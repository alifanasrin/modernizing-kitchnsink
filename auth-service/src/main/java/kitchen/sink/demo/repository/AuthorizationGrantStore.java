package kitchen.sink.demo.repository;

import kitchen.sink.demo.entity.AuthorizationGrant;

import java.time.Instant;
import java.util.Optional;

/** Authorization requests and codes. Every state change is one atomic Mongo operation (no read-then-write). */
public interface AuthorizationGrantStore {

    /** Stores a new request. */
    void save(AuthorizationGrant grant);

    /** Returns the unexpired request with this id that has no code yet. */
    Optional<AuthorizationGrant> findOpen(String requestId, Instant now);

    /**
     * Turns the open request into an issued code for this member (who passed the password check) and returns it, or
     * empty if the request is unknown, expired or already issued.
     */
    Optional<AuthorizationGrant> issueCode(String requestId, String userId, String codeHash, Instant now,
                                           Instant codeExpiresAt);

    /** Marks the unexpired grant for this code hash as USED and returns it; only one caller can ever get it. */
    Optional<AuthorizationGrant> consumeCode(String codeHash, Instant now);

    /** Returns the grant of this code hash if the code has already been used (until its TTL removes it). Not used by the token exchange: a replayed code is only refused. */
    Optional<AuthorizationGrant> findUsed(String codeHash);

    /** Records the session the grant's code was exchanged for (a record only, nothing revokes by it). */
    void attachSession(String grantId, String sessionId);
}
