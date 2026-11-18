package kitchen.sink.demo.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/**
 * The refresh token no longer names its session, so a session is found by the hash of its refresh token: unique
 * index on the current hash, sparse index on the previous one (replay detection). Sessions issued with the old
 * {@code <sessionId>.<secret>} tokens can no longer refresh; their owners log in again.
 */
@ChangeUnit(id = "create-session-refresh-token-indexes", order = "015", author = "demo", transactional = false)
public class V015_CreateSessionRefreshTokenIndexes {

    private static final String SESSION = "session";
    static final String CURRENT_INDEX = "uk_session_refresh_token_hash";
    static final String PREVIOUS_INDEX = "idx_session_previous_refresh_token_hash";

    /** Creates the refresh token hash indexes. */
    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(SESSION).createIndex(
                new Index().on("refreshTokenHash", Sort.Direction.ASC).unique().named(CURRENT_INDEX));
        mongoTemplate.indexOps(SESSION).createIndex(
                new Index().on("previousRefreshTokenHash", Sort.Direction.ASC).sparse().named(PREVIOUS_INDEX));
    }

    /** Drops the refresh token hash indexes. */
    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(SESSION).dropIndex(CURRENT_INDEX);
        mongoTemplate.indexOps(SESSION).dropIndex(PREVIOUS_INDEX);
    }
}
