package kitchen.sink.demo.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/** Unique index on the request id that {@code GET /oauth2/authorize} hands to the login page. */
@ChangeUnit(id = "create-oauth-request-id-index", order = "010", author = "demo", transactional = false)
public class V010_CreateAuthorizationRequestIdIndex {

    private static final String GRANT = "oauth_authorization";
    private static final String REQUEST_INDEX = "uk_oauth_authorization_request_id";

    /** Creates the sparse unique index (issued codes no longer have a request id). */
    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(GRANT).createIndex(
                new Index().on("requestId", Sort.Direction.ASC).unique().sparse().named(REQUEST_INDEX));
    }

    /** Drops the index. */
    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(GRANT).dropIndex(REQUEST_INDEX);
    }
}
