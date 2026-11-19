package kitchen.sink.demo.migration;

import com.mongodb.MongoWriteException;
import kitchen.sink.demo.util.MemberIdGenerator;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real MongoDB (localhost:27017), scratch database: runs each Mongock change unit's execute and rollback directly
 * (Mongock itself is disabled) and checks the resulting indexes, validators and data.
 */
@DataMongoTest(properties = {"spring.mongodb.database=membership_migration_test", "mongock.enabled=false"})
class MigrationsTest {

    @Autowired MongoTemplate mongo;

    @BeforeEach
    void setUp() {
        mongo.getDb().drop();
    }

    @AfterEach
    void tearDown() {
        mongo.getDb().drop();
    }

    private IndexInfo index(String collection, String name) {
        return mongo.indexOps(collection).getIndexInfo().stream()
                .filter(i -> name.equals(i.getName()))
                .findFirst()
                .orElse(null);
    }

    private List<String> indexNames(String collection) {
        return mongo.indexOps(collection).getIndexInfo().stream().map(IndexInfo::getName).toList();
    }

    private void insert(String collection, Document doc) {
        mongo.getCollection(collection).insertOne(doc);
    }

    @Nested
    class V001 {
        final V001_CreateUniqueEmailIndex migration = new V001_CreateUniqueEmailIndex();

        @Test
        @Tag("critical")
        void execute_createsUniqueEmailIndex() {
            migration.execute(mongo);

            IndexInfo info = index("member", "uk_member_email");
            assertThat(info).isNotNull();
            assertThat(info.isUnique()).isTrue();
            assertThat(info.getIndexFields()).extracting(f -> f.getKey()).containsExactly("email");
        }

        @Test
        @Tag("critical")
        void execute_rejectsDuplicateEmail() {
            migration.execute(mongo);
            insert("member", new Document("email", "a@b.com"));

            assertThatThrownBy(() -> insert("member", new Document("email", "a@b.com")))
                    .isInstanceOf(MongoWriteException.class);
        }

        @Test
        void rollback_dropsTheIndex_andAllowsDuplicates() {
            migration.execute(mongo);

            migration.rollback(mongo);

            assertThat(indexNames("member")).doesNotContain("uk_member_email");
            insert("member", new Document("email", "a@b.com"));
            insert("member", new Document("email", "a@b.com"));
            assertThat(mongo.getCollection("member").countDocuments()).isEqualTo(2);
        }
    }

    @Nested
    class V002 {
        final V002_AddNameMaxLengthValidator migration = new V002_AddNameMaxLengthValidator();

        @BeforeEach
        void createCollection() {
            mongo.createCollection("member");
        }

        @Test
        @Tag("critical")
        void execute_rejectsNameLongerThan25Characters() {
            migration.execute(mongo);

            assertThatThrownBy(() -> insert("member", new Document("name", "x".repeat(26))))
                    .isInstanceOf(MongoWriteException.class);
        }

        @Test
        void execute_acceptsNameOfExactly25Characters() {
            migration.execute(mongo);

            insert("member", new Document("name", "x".repeat(25)));

            assertThat(mongo.getCollection("member").countDocuments()).isEqualTo(1);
        }

        @Test
        void execute_rejectsNameThatIsNotAString() {
            migration.execute(mongo);

            assertThatThrownBy(() -> insert("member", new Document("name", 42)))
                    .isInstanceOf(MongoWriteException.class);
        }

        @Test
        void rollback_removesTheValidator() {
            migration.execute(mongo);

            migration.rollback(mongo);

            insert("member", new Document("name", "x".repeat(26)));
            assertThat(mongo.getCollection("member").countDocuments()).isEqualTo(1);
        }
    }

    @Nested
    class V006 {
        final V006_CreateSessionIndexes migration = new V006_CreateSessionIndexes();

        @Test
        void execute_createsMemberIdIndex() {
            migration.execute(mongo);

            IndexInfo info = index("session", "idx_session_member_id");
            assertThat(info.getIndexFields()).extracting(f -> f.getKey()).containsExactly("memberId");
            assertThat(info.getExpireAfter()).isEmpty();
        }

        @Test
        @Tag("critical")
        void execute_createsTtlIndexOnExpiresAtWithZeroDelay() {
            migration.execute(mongo);

            IndexInfo info = index("session", "ttl_session_expires_at");
            assertThat(info.getIndexFields()).extracting(f -> f.getKey()).containsExactly("expiresAt");
            assertThat(info.getExpireAfter()).contains(Duration.ZERO);
        }

        @Test
        void rollback_dropsBothIndexes() {
            migration.execute(mongo);

            migration.rollback(mongo);

            assertThat(indexNames("session")).doesNotContain("idx_session_member_id", "ttl_session_expires_at");
        }
    }

    @Nested
    class V009 {
        final V009_CreateOAuthIndexes migration = new V009_CreateOAuthIndexes();

        @Test
        @Tag("critical")
        void execute_createsUniqueClientIdIndex() {
            migration.execute(mongo);

            IndexInfo info = index("oauth_client", "uk_oauth_client_id");
            assertThat(info.isUnique()).isTrue();
            assertThat(info.getIndexFields()).extracting(f -> f.getKey()).containsExactly("clientId");
        }

        @Test
        void execute_rejectsDuplicateClientId() {
            migration.execute(mongo);
            insert("oauth_client", new Document("clientId", "web"));

            assertThatThrownBy(() -> insert("oauth_client", new Document("clientId", "web")))
                    .isInstanceOf(MongoWriteException.class);
        }

        @Test
        @Tag("critical")
        void execute_createsSparseUniqueChallengeIdAndCodeHashIndexes() {
            migration.execute(mongo);

            IndexInfo challenge = index("oauth_authorization", "uk_oauth_authorization_challenge_id");
            IndexInfo code = index("oauth_authorization", "uk_oauth_authorization_code_hash");
            assertThat(challenge.isUnique()).isTrue();
            assertThat(challenge.isSparse()).isTrue();
            assertThat(challenge.getIndexFields()).extracting(f -> f.getKey()).containsExactly("challengeId");
            assertThat(code.isUnique()).isTrue();
            assertThat(code.isSparse()).isTrue();
            assertThat(code.getIndexFields()).extracting(f -> f.getKey()).containsExactly("codeHash");
        }

        @Test
        void execute_allowsManyGrantsWithoutCodeHash_butRejectsDuplicateCodeHash() {
            migration.execute(mongo);
            insert("oauth_authorization", new Document("n", 1));
            insert("oauth_authorization", new Document("n", 2));
            insert("oauth_authorization", new Document("codeHash", "H1"));

            assertThatThrownBy(() -> insert("oauth_authorization", new Document("codeHash", "H1")))
                    .isInstanceOf(MongoWriteException.class);
        }

        @Test
        void execute_createsTtlIndexOnExpiresAtWithZeroDelay() {
            migration.execute(mongo);

            IndexInfo info = index("oauth_authorization", "ttl_oauth_authorization_expires_at");
            assertThat(info.getIndexFields()).extracting(f -> f.getKey()).containsExactly("expiresAt");
            assertThat(info.getExpireAfter()).contains(Duration.ZERO);
        }

        @Test
        void rollback_dropsAllFourIndexes() {
            migration.execute(mongo);

            migration.rollback(mongo);

            assertThat(indexNames("oauth_client")).doesNotContain("uk_oauth_client_id");
            assertThat(indexNames("oauth_authorization")).doesNotContain(
                    "uk_oauth_authorization_challenge_id",
                    "uk_oauth_authorization_code_hash",
                    "ttl_oauth_authorization_expires_at");
        }
    }

    @Nested
    class V010 {
        final V010_CreateAuthorizationRequestIdIndex migration = new V010_CreateAuthorizationRequestIdIndex();

        @Test
        @Tag("critical")
        void execute_createsSparseUniqueRequestIdIndex() {
            migration.execute(mongo);

            IndexInfo info = index("oauth_authorization", "uk_oauth_authorization_request_id");
            assertThat(info.isUnique()).isTrue();
            assertThat(info.isSparse()).isTrue();
            assertThat(info.getIndexFields()).extracting(f -> f.getKey()).containsExactly("requestId");
        }

        @Test
        void execute_allowsManyGrantsWithoutRequestId_butRejectsDuplicateRequestId() {
            migration.execute(mongo);
            insert("oauth_authorization", new Document("n", 1));
            insert("oauth_authorization", new Document("n", 2));
            insert("oauth_authorization", new Document("requestId", "R1"));

            assertThatThrownBy(() -> insert("oauth_authorization", new Document("requestId", "R1")))
                    .isInstanceOf(MongoWriteException.class);
        }

        @Test
        void rollback_dropsTheIndex() {
            migration.execute(mongo);

            migration.rollback(mongo);

            assertThat(indexNames("oauth_authorization")).doesNotContain("uk_oauth_authorization_request_id");
        }
    }



    @Nested
    class V015 {
        final V015_CreateSessionRefreshTokenIndexes migration = new V015_CreateSessionRefreshTokenIndexes();

        @Test
        @Tag("critical")
        void execute_createsAUniqueIndexOnTheCurrentHash_andASparseOneOnThePreviousHash() {
            migration.execute(mongo);

            IndexInfo current = index("session", "uk_session_refresh_token_hash");
            assertThat(current.isUnique()).isTrue();
            assertThat(current.getIndexFields()).extracting(f -> f.getKey()).containsExactly("refreshTokenHash");
            IndexInfo previous = index("session", "idx_session_previous_refresh_token_hash");
            assertThat(previous.isSparse()).isTrue();
            assertThat(previous.getIndexFields()).extracting(f -> f.getKey()).containsExactly("previousRefreshTokenHash");
        }

        @Test
        void execute_rejectsTwoSessionsWithTheSameRefreshTokenHash() {
            migration.execute(mongo);
            insert("session", new Document("_id", "s1").append("refreshTokenHash", "h"));

            assertThatThrownBy(() -> insert("session", new Document("_id", "s2").append("refreshTokenHash", "h")))
                    .isInstanceOf(MongoWriteException.class);
        }

        @Test
        void rollback_dropsBothIndexes() {
            migration.execute(mongo);

            migration.rollback(mongo);

            assertThat(indexNames("session")).doesNotContain("uk_session_refresh_token_hash",
                    "idx_session_previous_refresh_token_hash");
        }
    }
}
