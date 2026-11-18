package kitchen.sink.demo.repository;

import kitchen.sink.demo.entity.Session;

import java.time.Instant;

/** Single-document atomic operations; these must not be read-then-write. */
public interface SessionRepositoryCustom {

    /**
     * Finds the session whose current refresh token hash is {@code expectedHash} and that was issued to this
     * client (a null client only matches sessions that have none), replaces it with {@code newHash} and returns the
     * session; null if nothing matches.
     */
    Session rotateRefreshToken(String clientId, String expectedHash, String newHash, Instant now);
}
