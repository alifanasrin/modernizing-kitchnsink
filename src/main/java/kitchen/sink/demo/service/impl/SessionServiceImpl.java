package kitchen.sink.demo.service.impl;

import kitchen.sink.demo.constant.Constant;
import kitchen.sink.demo.entity.Session;
import kitchen.sink.demo.exception.AuthenticationFailedException;
import kitchen.sink.demo.exception.InvalidGrantException;
import kitchen.sink.demo.repository.SessionRepository;
import kitchen.sink.demo.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class SessionServiceImpl implements SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionServiceImpl.class);

    private final SessionRepository sessionRepository;
    private final Clock clock;

    public SessionServiceImpl(SessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    /** Saves the session of tokens that were created by the caller; stores only the hash of the refresh secret. */
    @Override
    @Transactional
    public void start(String sessionId, String userId, String clientId, String refreshTokenHash, String ipAddress,
                      Instant expiresAt) {
        log.info("session creation started");
        if (isBlank(sessionId) || isBlank(userId) || isBlank(clientId) || isBlank(refreshTokenHash) || expiresAt == null) {
            throw new IllegalArgumentException("a session needs an id, a member, a client and a refresh token hash");
        }
        Instant now = clock.instant();
        sessionRepository.insert(new Session(sessionId, userId, clientId, refreshTokenHash, now,
                expiresAt, ipAddress));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Rotates the refresh token hash; refuses a token no session holds. */
    @Override
    @Transactional
    public Session rotateRefreshToken(String clientId, String presentedHash, String newHash) {
        log.info("rotating refresh token in session");
        Instant now = clock.instant();
        Session rotated = sessionRepository.rotateRefreshToken(clientId, presentedHash, newHash, now);
        if (rotated == null) {
            log.error("no such refresh token present");
            throw new InvalidGrantException(Constant.BAD_GRANT);
        }
        return rotated;
    }

    /** Deletes the session with the given id; its refresh token stops working because nothing holds its hash. */
    @Override
    public void revoke(String sessionId) {
        log.info("revoking (deleting) session {}", sessionId);
        sessionRepository.deleteById(sessionId);
    }
}
