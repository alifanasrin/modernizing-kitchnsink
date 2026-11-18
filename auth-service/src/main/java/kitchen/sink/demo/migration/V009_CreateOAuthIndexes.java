package kitchen.sink.demo.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.time.Duration;

/** Indexes for the OAuth client registry and the authorization requests/codes. */
@ChangeUnit(id = "create-oauth-indexes", order = "009", author = "demo", transactional = false)
public class V009_CreateOAuthIndexes {

    private static final String CLIENT = "oauth_client";
    private static final String GRANT = "oauth_authorization";
    private static final String CLIENT_ID_INDEX = "uk_oauth_client_id";
    private static final String CHALLENGE_INDEX = "uk_oauth_authorization_challenge_id";
    private static final String CODE_INDEX = "uk_oauth_authorization_code_hash";
    private static final String TTL_INDEX = "ttl_oauth_authorization_expires_at";

    /** Creates the unique client id, challenge id and code hash indexes and the expiry TTL index. */
    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(CLIENT).createIndex(
                new Index().on("clientId", Sort.Direction.ASC).unique().named(CLIENT_ID_INDEX));
        // sparse: a pending request has no code hash and an issued one no challenge id
        mongoTemplate.indexOps(GRANT).createIndex(
                new Index().on("challengeId", Sort.Direction.ASC).unique().sparse().named(CHALLENGE_INDEX));
        mongoTemplate.indexOps(GRANT).createIndex(
                new Index().on("codeHash", Sort.Direction.ASC).unique().sparse().named(CODE_INDEX));
        mongoTemplate.indexOps(GRANT).createIndex(
                new Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO).named(TTL_INDEX));
    }

    /** Drops the indexes. */
    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(CLIENT).dropIndex(CLIENT_ID_INDEX);
        mongoTemplate.indexOps(GRANT).dropIndex(CHALLENGE_INDEX);
        mongoTemplate.indexOps(GRANT).dropIndex(CODE_INDEX);
        mongoTemplate.indexOps(GRANT).dropIndex(TTL_INDEX);
    }
}
