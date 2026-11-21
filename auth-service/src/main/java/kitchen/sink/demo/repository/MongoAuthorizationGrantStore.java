package kitchen.sink.demo.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kitchen.sink.demo.entity.AuthorizationGrant;
import kitchen.sink.demo.entity.AuthorizationGrant.Status;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class MongoAuthorizationGrantStore implements AuthorizationGrantStore {

    private static final Logger log = LoggerFactory.getLogger(MongoAuthorizationGrantStore.class);

    private final MongoTemplate mongoTemplate;

    public MongoAuthorizationGrantStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Inserts the pending request. */
    @Override
    public void save(AuthorizationGrant grant) {
        mongoTemplate.insert(grant);
    }

    /** Finds an unexpired request that has not been issued a code. */
    @Override
    public Optional<AuthorizationGrant> findOpen(String requestId, Instant now) {
        log.info("fins open authorization request");
        Query query = new Query(Criteria.where("requestId").is(requestId).and("status").is(Status.REQUESTED)
                .and("expiresAt").gt(now));
        return Optional.ofNullable(mongoTemplate.findOne(query, AuthorizationGrant.class));
    }

    /** Sets the member (user id), code hash and new expiry on the unexpired open request and clears its request id. */
    @Override
    public Optional<AuthorizationGrant> issueCode(String requestId, String userId, String codeHash, Instant now,
                                                  Instant codeExpiresAt) {
        Query query = new Query(Criteria.where("requestId").is(requestId).and("status").is(Status.REQUESTED)
                .and("expiresAt").gt(now));
        Update update = new Update().set("userId", userId).set("status", Status.CODE_ISSUED).set("codeHash", codeHash).set("expiresAt", codeExpiresAt).unset("requestId");
        return Optional.ofNullable(mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), AuthorizationGrant.class));
    }

    /** Marks the unexpired issued grant with this code hash as USED and returns it; only one caller can ever get it. */
    @Override
    public Optional<AuthorizationGrant> consumeCode(String codeHash, Instant now) {
        Query query = new Query(Criteria.where("codeHash").is(codeHash)
                .and("status").is(Status.CODE_ISSUED).and("expiresAt").gt(now));
        return Optional.ofNullable(mongoTemplate.findAndModify(query, new Update().set("status", Status.USED),
                AuthorizationGrant.class));
    }

    /** Finds the grant of a code that was already exchanged. */
    @Override
    public Optional<AuthorizationGrant> findUsed(String codeHash) {
        Query query = new Query(Criteria.where("codeHash").is(codeHash).and("status").is(Status.USED));
        return Optional.ofNullable(mongoTemplate.findOne(query, AuthorizationGrant.class));
    }

    /** Sets the session id on the grant. */
    @Override
    public void attachSession(String grantId, String sessionId) {
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(grantId)), new Update().set("sessionId", sessionId),
                AuthorizationGrant.class);
        log.debug("session {} attached to grant {}", sessionId, grantId);
    }
}
