package kitchen.sink.member.repository;

import com.mongodb.client.result.UpdateResult;
import kitchen.sink.member.entity.Member;
import org.bson.BsonObjectId;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoMemberStoreTest {

    static final Member MEMBER = new Member(null, "ann@example.com", "1234567890", "Ann", "Lee");

    @Mock MongoTemplate mongo;

    @Test
    void shouldReturnTrue_whenUpsertInserted() {
        when(mongo.upsert(any(Query.class), any(Update.class), eq(Member.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, new BsonObjectId(new ObjectId())));

        assertThat(new MongoMemberStore(mongo).createIfAbsent(MEMBER)).isTrue();
    }

    @Test
    void shouldReturnFalse_whenMemberExists() {
        when(mongo.upsert(any(Query.class), any(Update.class), eq(Member.class)))
                .thenReturn(UpdateResult.acknowledged(1, 0L, null));

        assertThat(new MongoMemberStore(mongo).createIfAbsent(MEMBER)).isFalse();
    }

    @Test
    void shouldReturnFalse_whenAConcurrentUpsertWonTheRace() {
        when(mongo.upsert(any(Query.class), any(Update.class), eq(Member.class)))
                .thenThrow(new DuplicateKeyException("E11000"));

        assertThat(new MongoMemberStore(mongo).createIfAbsent(MEMBER)).isFalse();
    }
}
