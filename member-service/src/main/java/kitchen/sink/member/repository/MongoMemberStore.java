package kitchen.sink.member.repository;

import kitchen.sink.member.entity.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MongoMemberStore implements MemberStore {

    private static final Logger log = LoggerFactory.getLogger(MongoMemberStore.class);

    private final MongoTemplate mongo;

    public MongoMemberStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** One atomic upsert that only sets fields on insert, so an existing member is never overwritten. */
    @Override
    public boolean createIfAbsent(Member member) {
        Update insertOnly = new Update()
                .setOnInsert("email", member.email())
                .setOnInsert("phoneNo", member.phoneNo())
                .setOnInsert("firstName", member.firstName())
                .setOnInsert("lastName", member.lastName());
        try {
            boolean created = mongo.upsert(Query.query(Criteria.where("email").is(member.email())),
                    insertOnly, Member.class).getUpsertedId() != null;
            log.debug("member upsert, created={}", created);
            return created;
        } catch (DuplicateKeyException e) {
            // two upserts of the same email raced on the unique index; the other one created it
            log.debug("member already created by a concurrent upsert");
            return false;
        }
    }
}
