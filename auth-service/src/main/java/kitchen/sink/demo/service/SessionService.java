package kitchen.sink.demo.service;

import kitchen.sink.demo.entity.Session;

import java.time.Instant;

/**
 * Changes to login sessions and nothing else: start, rotate the refresh token hash, revoke. It never creates, reads
 * or checks tokens; whoever issues the tokens (the grant handlers) creates them and
 * passes only the hash of the refresh secret here.
 */
public interface SessionService {

    /**
     * Stores a new session under {@code sessionId} for tokens the caller has already created: the session keeps only
     * the SHA-256 of the refresh-token secret ({@code refreshTokenHash}), the owner, the OAuth client and the IP.
     */
    void start(String sessionId, String userId, String clientId, String refreshTokenHash, String ipAddress,
               Instant expiresAt);

    /**
     * Atomically swaps the stored refresh token hash of the session that holds {@code presentedHash} as its
     * current one and was issued to {@code clientId}, and returns the session. The refresh token is opaque: the
     * session is found by its hash only. A token that no session holds as its current one
     * (already replaced, unknown, or issued to another client) is refused.
     *
     * @throws kitchen.sink.demo.exception.InvalidGrantException when nothing matched
     */
    Session rotateRefreshToken(String clientId, String presentedHash, String newHash);

    /** Deletes the session with this id (logout); does nothing if it is already gone. */
    void revoke(String sessionId);
}
