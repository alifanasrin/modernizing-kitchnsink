package kitchen.sink.member.config;

import kitchen.sink.member.exception.InvalidMemberEventException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.time.Duration;

@Configuration
public class KafkaConfig {

    /** Also declared by auth-service (the producer); declaring it twice is harmless and removes a start-up order. */
    @Bean
    NewTopic memberRegisteredTopic(@Value("${app.kafka.topic.member-registered}") String name,
                                   @Value("${app.kafka.topic.partitions}") int partitions,
                                   @Value("${app.kafka.topic.replicas}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }

    /** Same partition count as the main topic: a failed record goes to the same partition number. */
    @Bean
    NewTopic memberRegisteredDeadLetterTopic(@Value("${app.kafka.topic.dead-letter}") String name,
                                             @Value("${app.kafka.topic.partitions}") int partitions,
                                             @Value("${app.kafka.topic.replicas}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }

    /**
     * Retries transient failures (MongoDB down) with exponential backoff, then publishes the record to the
     * dead-letter topic. Invalid events are never retried. Spring Boot applies this handler to the listener
     * container automatically. Retries block the consumer thread.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> template,
                                          @Value("${app.kafka.topic.dead-letter}") String deadLetterTopic,
                                          @Value("${app.retry.max-retries}") int maxRetries,
                                          @Value("${app.retry.initial-interval}") Duration initialInterval,
                                          @Value("${app.retry.multiplier}") double multiplier) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(deadLetterTopic, record.partition()));
        var backOff = new ExponentialBackOffWithMaxRetries(Math.max(0, maxRetries));
        backOff.setInitialInterval(initialInterval.toMillis());
        backOff.setMultiplier(multiplier);
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidMemberEventException.class);
        return handler;
    }
}
