package kitchen.sink.demo.repository;

import kitchen.sink.demo.entity.AuthorizationGrant;
import kitchen.sink.demo.entity.AuthorizationGrant.Status;
import org.bson.Document;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Mockito only: {@code MongoTemplate} is mocked, so the queries and updates are checked, not run (no database). */
@ExtendWith(MockitoExtension.class)
class MongoAuthorizationGrantStoreUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    @Mock MongoTemplate mongoTemplate;
    @InjectMocks MongoAuthorizationGrantStore store;

    private static AuthorizationGrant grant() {
        return new AuthorizationGrant("req1", "spa", "https://app/cb", "challenge", NOW, NOW.plusSeconds(300));
    }

    private static Document expiresAfterNow() {
        return new Document("$gt", NOW);
    }

    // ---- save ----

    @Test
    void save_shouldInsertTheGrant() {
        AuthorizationGrant grant = grant();

        store.save(grant);

        verify(mongoTemplate).insert(grant);
    }

    @Test
    void save_shouldPropagateTheException_whenTheInsertFails() {
        AuthorizationGrant grant = grant();
        when(mongoTemplate.insert(grant)).thenThrow(new DataAccessResourceFailureException("mongo down"));

        assertThatThrownBy(() -> store.save(grant)).isInstanceOf(DataAccessResourceFailureException.class);
    }

    // ---- findOpen ----

    @Test
    void findOpen_shouldReturnTheGrant_whenAnUnexpiredRequestedGrantExists() {
        AuthorizationGrant grant = grant();
        when(mongoTemplate.findOne(any(Query.class), eq(AuthorizationGrant.class))).thenReturn(grant);

        Optional<AuthorizationGrant> result = store.findOpen("req1", NOW);

        assertThat(result).containsSame(grant);
    }

    @Test
    @Tag("critical")
    void findOpen_shouldQueryByRequestIdStatusRequestedAndNotExpired() {
        store.findOpen("req1", NOW);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findOne(query.capture(), eq(AuthorizationGrant.class));
        Document criteria = query.getValue().getQueryObject();
        assertThat(criteria).hasSize(3);
        assertThat(criteria.get("requestId")).isEqualTo("req1");
        assertThat(criteria.get("status")).isEqualTo(Status.REQUESTED);
        assertThat(criteria.get("expiresAt")).isEqualTo(expiresAfterNow());
    }

    @Test
    void findOpen_shouldReturnEmpty_whenNothingMatches() {
        assertThat(store.findOpen("unknown", NOW)).isEmpty();
    }

    // ---- issueCode ----

    @Test
    @Tag("critical")
    void issueCode_shouldQueryTheOpenUnexpiredRequest() {
        store.issueCode("req1", "user1", "hash", NOW, NOW.plusSeconds(60));

        Document criteria = captureFindAndModifyQuery().getQueryObject();
        assertThat(criteria).hasSize(3);
        assertThat(criteria.get("requestId")).isEqualTo("req1");
        assertThat(criteria.get("status")).isEqualTo(Status.REQUESTED);
        assertThat(criteria.get("expiresAt")).isEqualTo(expiresAfterNow());
    }

    @Test
    @Tag("critical")
    void issueCode_shouldSetMemberCodeHashStatusAndExpiryAndUnsetTheRequestId() {
        Instant codeExpiresAt = NOW.plusSeconds(60);

        store.issueCode("req1", "user1", "hash", NOW, codeExpiresAt);

        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class),
                eq(AuthorizationGrant.class));
        Document body = update.getValue().getUpdateObject();
        assertThat(body.get("$set", Document.class))
                .containsEntry("userId", "user1")
                .containsEntry("status", Status.CODE_ISSUED)
                .containsEntry("codeHash", "hash")
                .containsEntry("expiresAt", codeExpiresAt)
                .hasSize(4);
        assertThat(body.get("$unset", Document.class)).containsOnlyKeys("requestId");
    }

    @Test
    void issueCode_shouldReturnTheUpdatedGrant_whenOneMatches() {
        AuthorizationGrant updated = grant();
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(AuthorizationGrant.class))).thenReturn(updated);

        assertThat(store.issueCode("req1", "user1", "hash", NOW, NOW.plusSeconds(60))).containsSame(updated);
    }

    @Test
    void issueCode_shouldAskForTheDocumentAfterTheUpdate() {
        store.issueCode("req1", "user1", "hash", NOW, NOW.plusSeconds(60));

        ArgumentCaptor<FindAndModifyOptions> options = ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongoTemplate).findAndModify(any(Query.class), any(UpdateDefinition.class), options.capture(),
                eq(AuthorizationGrant.class));
        assertThat(options.getValue().isReturnNew()).isTrue();
    }

    @Test
    void issueCode_shouldReturnEmpty_whenNoOpenRequestMatches() {
        assertThat(store.issueCode("gone", "user1", "hash", NOW, NOW.plusSeconds(60))).isEmpty();
    }

    // ---- consumeCode ----

    @Test
    @Tag("critical")
    void consumeCode_shouldQueryTheIssuedUnexpiredCodeHash() {
        store.consumeCode("hash", NOW);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(query.capture(), any(UpdateDefinition.class),
                eq(AuthorizationGrant.class));
        Document criteria = query.getValue().getQueryObject();
        assertThat(criteria).hasSize(3);
        assertThat(criteria.get("codeHash")).isEqualTo("hash");
        assertThat(criteria.get("status")).isEqualTo(Status.CODE_ISSUED);
        assertThat(criteria.get("expiresAt")).isEqualTo(expiresAfterNow());
    }

    @Test
    @Tag("critical")
    void consumeCode_shouldOnlyMarkTheGrantUsed_inOneAtomicFindAndModify() {
        store.consumeCode("hash", NOW);

        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).findAndModify(any(Query.class), update.capture(), eq(AuthorizationGrant.class));
        Document body = update.getValue().getUpdateObject();
        assertThat(body).containsOnlyKeys("$set");
        assertThat(body.get("$set", Document.class)).containsOnly(java.util.Map.entry("status", Status.USED));
    }

    @Test
    void consumeCode_shouldReturnTheGrant_whenTheCodeWasIssuedAndNotExpired() {
        AuthorizationGrant grant = grant();
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                eq(AuthorizationGrant.class))).thenReturn(grant);

        assertThat(store.consumeCode("hash", NOW)).containsSame(grant);
    }

    @Test
    void consumeCode_shouldReturnEmpty_whenTheCodeIsUnknownUsedOrExpired() {
        assertThat(store.consumeCode("hash", NOW)).isEmpty();
    }

    // ---- findUsed ----

    @Test
    void findUsed_shouldQueryByCodeHashAndStatusUsed() {
        store.findUsed("hash");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findOne(query.capture(), eq(AuthorizationGrant.class));
        Document criteria = query.getValue().getQueryObject();
        assertThat(criteria).hasSize(2);
        assertThat(criteria.get("codeHash")).isEqualTo("hash");
        assertThat(criteria.get("status")).isEqualTo(Status.USED);
    }

    @Test
    void findUsed_shouldReturnTheGrant_whenTheCodeWasAlreadyExchanged() {
        AuthorizationGrant grant = grant();
        when(mongoTemplate.findOne(any(Query.class), eq(AuthorizationGrant.class))).thenReturn(grant);

        assertThat(store.findUsed("hash")).containsSame(grant);
    }

    @Test
    void findUsed_shouldReturnEmpty_whenNoUsedGrantMatches() {
        assertThat(store.findUsed("hash")).isEmpty();
    }

    // ---- attachSession ----

    @Test
    void attachSession_shouldSetTheSessionIdOnTheGrantWithThatId() {
        store.attachSession("grant1", "session1");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(query.capture(), update.capture(), eq(AuthorizationGrant.class));
        assertThat(query.getValue().getQueryObject()).containsOnly(java.util.Map.entry("_id", "grant1"));
        assertThat(update.getValue().getUpdateObject().get("$set", Document.class))
                .containsOnly(java.util.Map.entry("sessionId", "session1"));
    }

    @Test
    void attachSession_shouldPropagateTheException_whenTheUpdateFails() {
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class),
                eq(AuthorizationGrant.class))).thenThrow(new DataAccessResourceFailureException("mongo down"));

        assertThatThrownBy(() -> store.attachSession("grant1", "session1"))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    private Query captureFindAndModifyQuery() {
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(query.capture(), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(AuthorizationGrant.class));
        return query.getValue();
    }
}
