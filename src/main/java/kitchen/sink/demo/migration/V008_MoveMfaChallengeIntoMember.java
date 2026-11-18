package kitchen.sink.demo.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/**
 * MFA challenges are now embedded in the member document ({@code mfa}). Adds the index used to find a challenge by
 * its id and drops the old {@code mfa_challenge} collection (it only ever held codes valid for 5 minutes).
 */
@ChangeUnit(id = "embed-mfa-challenge-in-member", order = "008", author = "demo", transactional = false)
public class V008_MoveMfaChallengeIntoMember {

    private static final String INDEX = "uk_member_mfa_challenge_id";

    /** Creates the member MFA challenge id index and drops the old challenge collection. */
    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        // sparse: most members have no pending challenge
        mongoTemplate.indexOps("member").createIndex(
                new Index().on("mfa.challengeId", Sort.Direction.ASC).unique().sparse().named(INDEX));
        mongoTemplate.dropCollection("mfa_challenge");
    }

    /** Drops the member MFA challenge id index. */
    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps("member").dropIndex(INDEX);
        // the dropped collection held short-lived codes only and is not restored
    }
}
