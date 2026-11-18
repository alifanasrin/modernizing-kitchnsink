package kitchen.sink.demo.repository;

import kitchen.sink.demo.entity.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Real MongoDB (localhost:27017), scratch database: atomic refresh-token rotation and revocation. */
@DataMongoTest(properties = {"spring.mongodb.database=membership_session_test", "mongock.enabled=false"})
class SessionRepositoryTest {

    static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
    static final Instant LATER = NOW.plusSeconds(60);

    @Autowired SessionRepository sessions;
    @Autowired MongoTemplate mongo;

    @BeforeEach
    void setUp() {
        mongo.dropCollection(Session.class);
    }

    @AfterEach
    void tearDown() {
        mongo.getDb().drop();
    }

    private Session saveSession(String clientId, Instant expiresAt) {
        return saveSession(clientId, "hash-1", expiresAt);
    }

    private Session saveSession(String clientId, String hash, Instant expiresAt) {
        return mongo.insert(new Session("ABCD2345", clientId, hash, NOW, expiresAt, "1.1.1.1"));
    }

    @Test
    @Tag("critical")
    void rotateSwapsTheHashAndTouchesLastUsed_whenHashAndClientMatch() {
        Session session = saveSession("web", NOW.plusSeconds(3600));

        Session rotated = sessions.rotateRefreshToken("web", "hash-1", "hash-2", LATER);

        assertThat(rotated.getRefreshTokenHash()).isEqualTo("hash-2");
        assertThat(rotated.getLastUsedAt()).isEqualTo(LATER);
        assertThat(mongo.findById(session.getId(), Session.class).getRefreshTokenHash()).isEqualTo("hash-2");
    }

    @Test
    @Tag("critical")
    void rotateReturnsNull_andKeepsTheHash_whenTheOldHashIsReplayed() {
        Session session = saveSession("web", NOW.plusSeconds(3600));
        sessions.rotateRefreshToken("web", "hash-1", "hash-2", LATER);

        Session replay = sessions.rotateRefreshToken("web", "hash-1", "hash-3", LATER);

        assertThat(replay).isNull();
        assertThat(mongo.findById(session.getId(), Session.class).getRefreshTokenHash()).isEqualTo("hash-2");
    }

    @Test
    @Tag("critical")
    void rotateReturnsNull_whenAnotherClientPresentsTheToken() {
        Session session = saveSession("web", NOW.plusSeconds(3600));

        Session rotated = sessions.rotateRefreshToken("mobile", "hash-1", "hash-2", LATER);

        assertThat(rotated).isNull();
        assertThat(mongo.findById(session.getId(), Session.class).getRefreshTokenHash()).isEqualTo("hash-1");
    }

    @Test
    void rotateWithoutAClient_onlyMatchesASessionThatHasNone() {
        saveSession("web", "hash-a", NOW.plusSeconds(3600));
        saveSession(null, "hash-b", NOW.plusSeconds(3600));

        assertThat(sessions.rotateRefreshToken(null, "hash-a", "hash-2", LATER)).isNull();
        assertThat(sessions.rotateRefreshToken(null, "hash-b", "hash-3", LATER)).isNotNull();
    }
}
