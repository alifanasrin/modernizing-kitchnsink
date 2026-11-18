package kitchen.sink.demo.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.time.Duration;

@ChangeUnit(id = "create-session-indexes", order = "006", author = "demo", transactional = false)
public class V006_CreateSessionIndexes {

    private static final String COLLECTION = "session";
    private static final String MEMBER_INDEX = "idx_session_member_id";
    private static final String TTL_INDEX = "ttl_session_expires_at";

    /** Creates the session indexes, including the expiry TTL index. */
    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(COLLECTION)
                .createIndex(new Index().on("memberId", Sort.Direction.ASC).named(MEMBER_INDEX));
        // MongoDB deletes the session document once expiresAt has passed.
        mongoTemplate.indexOps(COLLECTION)
                .createIndex(new Index().on("expiresAt", Sort.Direction.ASC)
                        .expire(Duration.ZERO).named(TTL_INDEX));
    }

    /** Drops the session indexes. */
    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(COLLECTION).dropIndex(MEMBER_INDEX);
        mongoTemplate.indexOps(COLLECTION).dropIndex(TTL_INDEX);
    }
}
