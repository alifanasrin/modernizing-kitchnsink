package com.demo.notification.listener;

import com.demo.notification.service.FailureRecorder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeadLetterRecovererTest {

    @Mock KafkaOperations<Object, Object> template;
    @Mock FailureRecorder recorder;

    @Test
    void recordsTheFailureThenPublishesToTheDeadLetterTopicOnTheSamePartition() {
        lenient().when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        var recoverer = new DeadLetterRecoverer(template, recorder, "notification.high-priority.DLT");
        var failure = new IllegalStateException("boom");

        recoverer.accept(new ConsumerRecord<>("notification.high-priority", 2, 41L, "k", "payload"), failure);

        verify(recorder).recordFailure("payload", failure, "notification.high-priority", 2, 41L);
        ArgumentCaptor<ProducerRecord<Object, Object>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("notification.high-priority.DLT");
        assertThat(sent.getValue().partition()).isEqualTo(2);
        assertThat(sent.getValue().value()).isEqualTo("payload");
    }

    @Test
    void skipsTheAuditTrailWhenTheValueIsNotAString() {
        lenient().when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        var recoverer = new DeadLetterRecoverer(template, recorder, "dlt");

        recoverer.accept(new ConsumerRecord<>("t", 0, 1L, "k", new byte[] {1}), new RuntimeException("x"));

        verify(recorder, never()).recordFailure(anyString(), any(), anyString(), anyInt(), anyLong());
        verify(template).send(any(ProducerRecord.class));
    }
}
