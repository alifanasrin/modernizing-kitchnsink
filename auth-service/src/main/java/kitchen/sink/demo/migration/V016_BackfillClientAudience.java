package kitchen.sink.demo.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

/**
 * Clients now carry an {@code aud} list for their access tokens. Before, the audience was the client's {@code name};
 * a client without {@code aud} is given {@code [name]} so it keeps working with the same audience.
 */
@ChangeUnit(id = "backfill-oauth-client-audience", order = "016", author = "demo", transactional = false)
public class V016_BackfillClientAudience {

    private static final String CLIENT = "oauth_client";

    /** Sets {@code aud = [name]} on every client that has a name but no audience yet. */
    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        Query missing = new Query(new Criteria().andOperator(
                Criteria.where("aud").exists(false), Criteria.where("name").exists(true)));
        for (Document client : mongoTemplate.getCollection(CLIENT).find(missing.getQueryObject())) {
            Object name = client.get("name");
            if (name instanceof String n && !n.isBlank()) {
                mongoTemplate.getCollection(CLIENT).updateOne(new Document("_id", client.get("_id")),
                        new Document("$set", new Document("aud", List.of(n))));
            }
        }
    }

    /** Nothing to undo: the audience stays harmlessly on the clients. */
    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        // intentionally empty
    }
}
