package kitchen.sink.demo.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@ChangeUnit(id = "create-unique-email-index", order = "001", author = "demo", transactional = false)
public class V001_CreateUniqueEmailIndex {

    private static final String COLLECTION = "member";
    private static final String INDEX_NAME = "uk_member_email";

    /** Creates the unique index on member email. */
    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(COLLECTION)
                .createIndex(new Index().on("email", Sort.Direction.ASC).unique().named(INDEX_NAME));
    }

    /** Drops the unique email index. */
    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(COLLECTION).dropIndex(INDEX_NAME);
    }
}
