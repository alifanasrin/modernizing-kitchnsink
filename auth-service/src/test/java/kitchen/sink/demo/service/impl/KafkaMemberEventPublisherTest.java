package kitchen.sink.demo.service.impl;

import kitchen.sink.demo.event.MemberRegistered;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaMemberEventPublisherTest {

    static final MemberRegistered REGISTERED =
            new MemberRegistered("64f0", "ann@example.com", "1234567890", "Ann", "Lee");

    @Mock
    KafkaOperations<String, String> kafka;

    KafkaMemberEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaMemberEventPublisher(kafka, JsonMapper.builder().build(), "member.registered");
    }

    @Test
    @Tag("critical")
    void shouldSendVersionedEventKeyedByMemberId_withMemberDataOnly() throws Exception {
        when(kafka.send(eq("member.registered"), eq("64f0"), anyString()))
                .thenReturn(new CompletableFuture<SendResult<String, String>>());

        publisher.onMemberRegistered(REGISTERED);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("member.registered"), eq("64f0"), payload.capture());
        JsonNode json = JsonMapper.builder().build().readTree(payload.getValue());
        assertThat(json.get("data").get("email").asString()).isEqualTo("ann@example.com");
        assertThat(json.get("data").get("firstName").asString()).isEqualTo("Ann");
        assertThat(payload.getValue()).doesNotContainIgnoringCase("password");
    }

    @Test
    void shouldNotThrow_whenKafkaRejectsTheSend() {
        when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("broker down"));

        assertThatCode(() -> publisher.onMemberRegistered(REGISTERED)).doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrow_whenTheAsyncSendFails() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("timeout")));

        assertThatCode(() -> publisher.onMemberRegistered(REGISTERED)).doesNotThrowAnyException();
    }
}
