package com.demo.notification.listener;

import com.demo.notification.service.FailureRecorder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.stereotype.Component;

/**
 * What happens to a record once retries are over: leave the audit trail ({@link FailureRecorder}), then publish
 * the original record to the dead-letter topic on the same partition number.
 */
@Component
public class DeadLetterRecoverer implements ConsumerRecordRecoverer {

    private final FailureRecorder recorder;
    private final DeadLetterPublishingRecoverer publisher;

    public DeadLetterRecoverer(KafkaOperations<Object, Object> template, FailureRecorder recorder,
                               @Value("${app.kafka.topic.dead-letter}") String deadLetterTopic) {
        this.recorder = recorder;
        this.publisher = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(deadLetterTopic, record.partition()));
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception ex) {
        if (record.value() instanceof String payload) {
            recorder.recordFailure(payload, ex, record.topic(), record.partition(), record.offset());
        }
        publisher.accept(record, ex);
    }
}
