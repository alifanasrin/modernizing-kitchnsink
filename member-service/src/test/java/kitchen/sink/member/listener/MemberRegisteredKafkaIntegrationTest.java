package kitchen.sink.member.listener;

import kitchen.sink.member.entity.Member;
import kitchen.sink.member.repository.MemberStore;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/** Real listener container and error handler on an embedded broker; only the MongoDB store is mocked. */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"member.registered", "member.registered.DLT"})
@TestPropertySource(locations = "classpath:jwt-test.properties", properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=false",
        "app.kafka.topic.partitions=1"})
class MemberRegisteredKafkaIntegrationTest {

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired EmbeddedKafkaBroker broker;
    @MockitoBean MemberStore store;

    @Test
    @Tag("critical")
    void shouldCreateMember_whenValidEventIsPublished() {
        kafka.send("member.registered", "64f0", """
                {"eventId":"e1","eventType":"MEMBER_REGISTERED","schemaVersion":"1.0","source":"auth-service",
                 "data":{"memberId":"64f0","email":"ann@example.com","phoneNo":"1234567890","firstName":"Ann","lastName":"Lee"}}
                """);

        verify(store, timeout(10_000)).createIfAbsent(new Member(null, "ann@example.com", "1234567890", "Ann", "Lee"));
    }

    @Test
    @Tag("critical")
    void shouldDeadLetterWithoutRetry_whenEventIsInvalid() {
        kafka.send("member.registered", "bad", "this is not json");

        try (Consumer<String, String> dlt = new DefaultKafkaConsumerFactory<>(
                KafkaTestUtils.consumerProps(broker, "dlt-check", true),
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(dlt, "member.registered.DLT");
            var record = KafkaTestUtils.getSingleRecord(dlt, "member.registered.DLT", Duration.ofSeconds(15));
            assertThat(record.value()).isEqualTo("this is not json");
        }
        verify(store, never()).createIfAbsent(any());
    }
}
