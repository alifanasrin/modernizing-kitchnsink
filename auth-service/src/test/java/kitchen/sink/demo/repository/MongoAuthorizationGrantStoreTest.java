package kitchen.sink.demo.repository;

import kitchen.sink.demo.entity.AuthorizationGrant;
import kitchen.sink.demo.entity.AuthorizationGrant.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Real MongoDB (needs one at localhost:27017), in a scratch database. Checks the grant status transitions. */
@DataMongoTest(properties = {"spring.mongodb.database=membership_grant_test", "mongock.enabled=false"})
@Import(MongoAuthorizationGrantStore.class)
class MongoAuthorizationGrantStoreTest {

    static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    @Autowired MongoAuthorizationGrantStore store;
    @Autowired MongoTemplate mongo;

    @BeforeEach
    void setUp() {
        mongo.dropCollection(AuthorizationGrant.class);
        store.save(new AuthorizationGrant("req1", "spa", "https://app/cb", "challenge", NOW, NOW.plusSeconds(300)));
    }

    @AfterEach
    void tearDown() {
        mongo.getDb().drop();
    }

    private AuthorizationGrant issue() {
        return store.issueCode("req1", "ABCD2345", "hash", NOW, NOW.plusSeconds(60)).orElseThrow();
    }

    @Test
    @Tag("critical")
    void statusMovesForwardThroughTheWholeFlow() {
        assertThat(mongo.findAll(AuthorizationGrant.class).get(0).getStatus()).isEqualTo(Status.REQUESTED);
        assertThat(store.issueCode("req1", "ABCD2345", "hash", NOW, NOW.plusSeconds(60)).orElseThrow().getStatus())
                .isEqualTo(Status.CODE_ISSUED);
        store.consumeCode("hash", NOW);
        assertThat(mongo.findAll(AuthorizationGrant.class).get(0).getStatus()).isEqualTo(Status.USED);
    }

    @Test
    @Tag("critical")
    void aCodeCanOnlyBeConsumedOnce_andStaysFindableAsUsed() {
        issue();

        assertThat(store.consumeCode("hash", NOW)).isPresent();
        assertThat(store.consumeCode("hash", NOW)).isEmpty();
        assertThat(store.findUsed("hash")).isPresent();
    }

    @Test
    void anUnusedCode_isNotReportedAsUsed() {
        issue();

        assertThat(store.findUsed("hash")).isEmpty();
    }

    @Test
    @Tag("critical")
    void aUsedGrantCannotBeOpenedOrIssuedAgain() {
        issue();
        store.consumeCode("hash", NOW);

        assertThat(store.findOpen("req1", NOW)).isEmpty();
        assertThat(store.issueCode("req1", "ABCD2345", "hash2", NOW, NOW.plusSeconds(60))).isEmpty();
    }

    @Test
    void attachSession_isReadBackOnTheUsedGrant() {
        AuthorizationGrant grant = issue();
        store.consumeCode("hash", NOW);

        store.attachSession(grant.getId(), "sess1");

        assertThat(store.findUsed("hash").orElseThrow().getSessionId()).isEqualTo("sess1");
    }
}
