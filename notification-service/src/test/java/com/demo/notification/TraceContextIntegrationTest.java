package com.demo.notification;

import com.demo.notification.message.EmailMessage;
import com.demo.notification.repository.NotificationLogRepository;
import com.demo.notification.service.EmailSender;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** The W3C {@code traceparent} header on a consumed record becomes the trace id in the log MDC while it is handled. */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "app.kafka.topic.partitions=1",
        // the test template would otherwise start a new trace and overwrite the header set below
        "spring.kafka.template.observation-enabled=false"
})
@EmbeddedKafka(topics = {"notification.high-priority", "notification.high-priority.DLT"}, partitions = 1)
@DirtiesContext
class TraceContextIntegrationTest {

    static final CompletableFuture<String> TRACE_ID_AT_SEND = new CompletableFuture<>();

    @TestConfiguration
    static class MdcCapturingSender {
        @Bean
        @Primary
        EmailSender capturingSender() {
            return (EmailMessage m) -> TRACE_ID_AT_SEND.complete(MDC.get("traceId"));
        }
    }

    @MockitoBean NotificationLogRepository repository;

    @Autowired KafkaTemplate<String, String> template;

    static final String EVENT = """
            {"eventId":"e-trace","eventType":"NOTIFICATION_REQUESTED","schemaVersion":"1.0",
             "occurredAt":"2099-01-01T00:00:00Z","source":"auth-service","correlationId":"c-1",
             "notification":{"channel":"EMAIL","notificationType":"MFA_CODE","expiresAt":"2099-01-01T01:00:00Z"},
             "recipient":{"userId":"u-1","email":"ann@example.com","name":"Ann"},
             "data":{"code":"482913","validForSeconds":300}}""";

    @Test
    void traceparentHeaderOfTheRecordIsTheTraceIdWhileTheEventIsProcessed() throws Exception {
        var record = new ProducerRecord<>("notification.high-priority", "u-1", EVENT);
        record.headers().add("traceparent",
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01".getBytes(StandardCharsets.UTF_8));

        template.send(record);

        assertThat(TRACE_ID_AT_SEND.get(30, TimeUnit.SECONDS)).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }
}
