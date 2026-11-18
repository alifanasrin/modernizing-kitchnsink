package com.demo.notification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import com.demo.notification.exception.InvalidNotificationException;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic notificationRequestsTopic(@Value("${app.kafka.topic.requests}") String name,
                                       @Value("${app.kafka.topic.partitions}") int partitions,
                                       @Value("${app.kafka.topic.replicas}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }

    /** Same partition count as the main topic: failed records are published to the same partition number. */
    @Bean
    NewTopic notificationRequestsDeadLetterTopic(@Value("${app.kafka.topic.dead-letter}") String name,
                                                 @Value("${app.kafka.topic.partitions}") int partitions,
                                                 @Value("${app.kafka.topic.replicas}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }

    /**
     * Retries transient send failures with exponential backoff (max-retries, initial-interval, multiplier), then
     * marks the record FAILED and publishes the event to the dead-letter topic. Invalid events skip the retries.
     * Spring Boot applies this handler to the listener container automatically.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(ConsumerRecordRecoverer recoverer,
                                          @Value("${app.retry.max-retries}") int maxRetries,
                                          @Value("${app.retry.initial-interval}") java.time.Duration initial,
                                          @Value("${app.retry.multiplier}") double multiplier) {
        var backOff = new ExponentialBackOffWithMaxRetries(Math.max(0, maxRetries));
        backOff.setInitialInterval(initial.toMillis());
        backOff.setMultiplier(multiplier);
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidNotificationException.class);
        return handler;
    }
}
