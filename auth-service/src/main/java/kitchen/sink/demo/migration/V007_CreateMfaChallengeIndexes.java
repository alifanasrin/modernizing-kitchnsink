package kitchen.sink.demo.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.time.Duration;

@ChangeUnit(id = "create-mfa-challenge-indexes", order = "007", author = "demo", transactional = false)
public class V007_CreateMfaChallengeIndexes {

    private static final String COLLECTION = "mfa_challenge";
    private static final String TTL_INDEX = "ttl_mfa_challenge_expires_at";

    /** Creates the TTL index on the old MFA challenge collection. */
    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        // MongoDB deletes the challenge once expiresAt has passed.
        mongoTemplate.indexOps(COLLECTION)
                .createIndex(new Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO).named(TTL_INDEX));
    }

    /** Drops the MFA challenge TTL index. */
    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(COLLECTION).dropIndex(TTL_INDEX);
    }
}
