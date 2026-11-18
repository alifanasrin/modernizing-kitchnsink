package kitchen.sink.demo.migration;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import kitchen.sink.demo.util.MemberIdGenerator;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.util.HashSet;
import java.util.Set;

@ChangeUnit(id = "add-member-id-unique-index", order = "004", author = "demo", transactional = false)
public class V004_AddMemberIdUniqueIndex {

    private static final String COLLECTION = "member";
    private static final String INDEX_NAME = "uk_member_member_id";

    /** Backfills missing member ids and creates the unique member id index. */
    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        var members = mongoTemplate.getCollection(COLLECTION);

        // Backfill members created before memberId existed; a unique index cannot hold several missing values.
        Set<String> used = new HashSet<>();
        members.find(Filters.exists("memberId")).forEach(d -> used.add(d.getString("memberId")));
        for (Document doc : members.find(Filters.or(Filters.exists("memberId", false),
                Filters.eq("memberId", null)))) {
            String id;
            do {
                id = MemberIdGenerator.generate();
            } while (!used.add(id));
            members.updateOne(Filters.eq("_id", doc.get("_id")), Updates.set("memberId", id));
        }

        mongoTemplate.indexOps(COLLECTION)
                .createIndex(new Index().on("memberId", Sort.Direction.ASC).unique().named(INDEX_NAME));
    }

    /** Drops the unique member id index. */
    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(COLLECTION).dropIndex(INDEX_NAME);
    }
}
