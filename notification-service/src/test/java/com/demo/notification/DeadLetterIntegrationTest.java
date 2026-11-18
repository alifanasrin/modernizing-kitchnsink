package com.demo.notification;

import com.demo.notification.message.EmailMessage;

import com.demo.notification.repository.NotificationLogRepository;
import com.demo.notification.message.EmailMessage;
import com.demo.notification.exception.NotificationSendException;
import com.demo.notification.service.EmailSender;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Real listener + error handler on an embedded broker: a send that always fails ends up on the DLT. */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "app.retry.max-retries=3",
        "app.retry.initial-interval=50ms",
        "app.retry.multiplier=1.0",
        "app.kafka.topic.partitions=1"
})
@EmbeddedKafka(topics = {"notification.high-priority", "notification.high-priority.DLT"}, partitions = 1)
@DirtiesContext
class DeadLetterIntegrationTest {

    static final AtomicInteger SEND_CALLS = new AtomicInteger();

    @TestConfiguration
    static class AlwaysFailingSender {
        @Bean
        @Primary
        EmailSender failingSender() {
            return (EmailMessage m) -> {
                SEND_CALLS.incrementAndGet();
                throw new NotificationSendException("smtp down", true, null);
            };
        }
    }

    @MockitoBean NotificationLogRepository repository;

    @Autowired KafkaTemplate<String, String> template;
    @Autowired EmbeddedKafkaBroker broker;

    static final String EVENT = """
            {"eventId":"e-dlt","eventType":"NOTIFICATION_REQUESTED","schemaVersion":"1.0",
             "occurredAt":"2099-01-01T00:00:00Z","source":"auth-service","correlationId":"c-1",
             "notification":{"channel":"EMAIL","notificationType":"MFA_CODE","expiresAt":"2099-01-01T01:00:00Z"},
             "recipient":{"userId":"u-1","email":"ann@example.com","name":"Ann"},
             "data":{"code":"482913","validForSeconds":300}}""";

    @Test
    void failedSendIsRetriedThreeTimesThenPublishedToDeadLetterTopic() {
        template.send("notification.high-priority", "u-1", EVENT);

        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "dlt-test", true);
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "notification.high-priority.DLT");
            ConsumerRecord<String, String> dead = KafkaTestUtils.getSingleRecord(consumer,
                    "notification.high-priority.DLT", Duration.ofSeconds(30));

            assertThat(dead.value()).contains("e-dlt");
            assertThat(SEND_CALLS.get()).isEqualTo(4); // first delivery + 3 retries
        }
    }
}
