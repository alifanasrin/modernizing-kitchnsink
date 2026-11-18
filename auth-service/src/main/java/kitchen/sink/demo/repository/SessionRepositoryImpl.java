package kitchen.sink.demo.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kitchen.sink.demo.entity.Session;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

public class SessionRepositoryImpl implements SessionRepositoryCustom {

    private static final Logger log = LoggerFactory.getLogger(SessionRepositoryImpl.class);

    private final MongoTemplate mongoTemplate;

    public SessionRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Updates the refresh token hash and last-used time in one atomic operation. Expired and logged-out sessions are
     * not filtered here: the TTL index and the delete at logout remove them from the collection.
     */
    @Override
    public Session rotateRefreshToken(String clientId, String expectedHash, String newHash, Instant now) {
        Query query = new Query(Criteria.where("refreshTokenHash").is(expectedHash).and("clientId").is(clientId));
        Update update = new Update().set("refreshTokenHash", newHash)
                .set("lastUsedAt", now);
        Session rotated = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Session.class);
        if (rotated == null) {
            log.debug("no active session holds the presented refresh token for this client");
        }
        return rotated;
    }
}
