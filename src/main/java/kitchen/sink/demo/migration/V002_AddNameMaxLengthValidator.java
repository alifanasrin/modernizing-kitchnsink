package kitchen.sink.demo.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeUnit(id = "add-name-max-length-validator", order = "002", author = "demo", transactional = false)
public class V002_AddNameMaxLengthValidator {

    private static final String COLLECTION = "member";
    private static final int NAME_MAX_LENGTH = 25;

    /** Adds a schema validator limiting member name to 25 characters. */
    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        Document schema = new Document("bsonType", "object")
                .append("properties", new Document("name", new Document()
                        .append("bsonType", "string")
                        .append("maxLength", NAME_MAX_LENGTH)
                        .append("description", "name must not exceed " + NAME_MAX_LENGTH + " characters")));

        mongoTemplate.getDb().runCommand(new Document("collMod", COLLECTION)
                .append("validator", new Document("$jsonSchema", schema))
                .append("validationLevel", "moderate")
                .append("validationAction", "error"));
    }

    /** Removes the name length validator. */
    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.getDb().runCommand(new Document("collMod", COLLECTION)
                .append("validator", new Document())
                .append("validationLevel", "off"));
    }
}
