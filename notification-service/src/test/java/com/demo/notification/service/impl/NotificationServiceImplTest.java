package com.demo.notification.service.impl;

import com.demo.notification.service.EmailSender;
import com.demo.notification.service.NotificationService;
import com.demo.notification.service.FailureRecorder;

import com.demo.notification.message.EmailMessage;
import com.demo.notification.service.impl.EmailChannel;

import com.demo.notification.exception.InvalidNotificationException;
import com.demo.notification.exception.NotificationSendException;
import com.demo.notification.model.NotificationLog;
import com.demo.notification.model.NotificationStatus;
import com.demo.notification.repository.NotificationLogRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    @Mock NotificationLogRepository repository;
    @Mock EmailSender sender;

    NotificationService service;
    FailureRecorder recorder;

    @BeforeEach
    void setUp() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var renderer = new TemplateRendererImpl("no-reply@example.com");
        var mapper = JsonMapper.builder().build();
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var parser = new NotificationEventParserImpl(mapper, validator, renderer,
                List.of(new EmailChannel(renderer, sender)));
        service = new NotificationServiceImpl(parser, repository, clock);
        recorder = new FailureRecorderImpl(mapper, repository, clock);
    }

    static String event(String eventId, String expiresAt, String data) {
        return """
                {"eventId":"%s","eventType":"NOTIFICATION_REQUESTED","schemaVersion":"1.0",
                 "occurredAt":"2026-11-03T09:59:50Z","source":"auth-service","correlationId":"corr-1",
                 "notification":{"channel":"EMAIL","notificationType":"MFA_CODE","expiresAt":"%s"},
                 "recipient":{"userId":"u-1","email":"ann@example.com","name":"Ann"},
                 "data":%s}""".formatted(eventId, expiresAt, data);
    }

    static final String GOOD_DATA = "{\"code\":\"482913\",\"validForSeconds\":300}";

    @Test
    @Tag("critical")
    void sendsEmailAndMarksSent() {
        when(repository.findBySourceAndEventId("auth-service", "e1")).thenReturn(Optional.empty());

        service.process(event("e1", "2026-11-03T10:05:00Z", GOOD_DATA));

        ArgumentCaptor<EmailMessage> msg = ArgumentCaptor.forClass(EmailMessage.class);
        verify(sender).send(msg.capture());
        assertThat(msg.getValue().to()).isEqualTo("ann@example.com");
        assertThat(msg.getValue().textBody()).contains("482913");

        ArgumentCaptor<NotificationLog> saved = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository, times(2)).save(saved.capture());
        NotificationLog last = saved.getAllValues().get(1);
        assertThat(last.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(last.getSentAt()).isEqualTo(NOW);
        assertThat(last.getRecipientAddress()).isEqualTo("a***@example.com");
        assertThat(last.getNotificationType()).isEqualTo("MFA_CODE");
    }

    @Test
    @Tag("critical")
    void skipsDuplicateOfAlreadySentEvent() {
        NotificationLog done = new NotificationLog("auth-service", "e1", "EMAIL", "MFA_CODE", "u-1", "a***@example.com", NOW, NOW);
        done.markSent(NOW);
        when(repository.findBySourceAndEventId("auth-service", "e1")).thenReturn(Optional.of(done));

        service.process(event("e1", "2026-11-03T10:05:00Z", GOOD_DATA));

        verify(sender, never()).send(any());
        verify(repository, never()).save(any());
    }

    @Test
    @Tag("critical")
    void marksExpiredAndDoesNotSend() {
        when(repository.findBySourceAndEventId("auth-service", "e1")).thenReturn(Optional.empty());

        service.process(event("e1", "2026-11-03T09:59:00Z", GOOD_DATA));

        verify(sender, never()).send(any());
        ArgumentCaptor<NotificationLog> saved = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.EXPIRED);
    }

    @Test
    void rejectsMalformedPayload() {
        assertThatThrownBy(() -> service.process("not json"))
                .isInstanceOf(InvalidNotificationException.class);
    }

    @Test
    void rejectsMissingCode() {
        assertThatThrownBy(() -> service.process(event("e1", "2026-11-03T10:05:00Z", "{\"validForSeconds\":300}")))
                .isInstanceOf(InvalidNotificationException.class);
        verify(sender, never()).send(any());
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsUnsupportedMajorVersion() {
        String v2 = event("e1", "2026-11-03T10:05:00Z", GOOD_DATA).replace("\"1.0\"", "\"2.0\"");
        assertThatThrownBy(() -> service.process(v2)).isInstanceOf(InvalidNotificationException.class)
                .hasMessageContaining("schemaVersion");
    }

    @Test
    void rejectsTypesOutsideTheHighPriorityList() {
        String promo = event("e1", "2026-11-03T10:05:00Z", GOOD_DATA).replace("MFA_CODE", "NEWSLETTER");
        assertThatThrownBy(() -> service.process(promo)).isInstanceOf(InvalidNotificationException.class)
                .hasMessageContaining("NEWSLETTER is not accepted on the requests topic")
                .hasMessageContaining("ACCOUNT_BLOCKED, FORGOT_PASSWORD, MFA_CODE, OTP, VERIFY_EMAIL");
    }

    @Test
    void ignoresAProducersLeftoverPhoneField() {
        when(repository.findBySourceAndEventId("auth-service", "e1")).thenReturn(Optional.empty());

        service.process(event("e1", "2026-11-03T10:05:00Z", GOOD_DATA).replace("\"name\":\"Ann\"", "\"phone\":null,\"name\":\"Ann\""));

        verify(sender).send(any());
    }

    @Test
    void rejectsUnsupportedChannel() {
        for (String channel : new String[] {"PUSH", "SMS"}) { // SMS is no longer supported
            String payload = event("e1", "2026-11-03T10:05:00Z", GOOD_DATA).replace("\"EMAIL\"", "\"" + channel + "\"");
            assertThatThrownBy(() -> service.process(payload)).isInstanceOf(InvalidNotificationException.class)
                    .hasMessageContaining("Unsupported channel");
        }
        verify(sender, never()).send(any());
    }


    @Test
    void rejectsEmailWithoutAddress() {
        String noEmail = event("e1", "2026-11-03T10:05:00Z", GOOD_DATA).replace("\"email\":\"ann@example.com\",", "");
        assertThatThrownBy(() -> service.process(noEmail)).isInstanceOf(InvalidNotificationException.class)
                .hasMessageContaining("recipient.email");
    }

    @Test
    void retryableSendFailureIsRethrownAndAttemptRecorded() {
        when(repository.findBySourceAndEventId("auth-service", "e1")).thenReturn(Optional.empty());
        doThrow(new NotificationSendException("timeout", true, null)).when(sender).send(any());

        assertThatThrownBy(() -> service.process(event("e1", "2026-11-03T10:05:00Z", GOOD_DATA)))
                .isInstanceOf(NotificationSendException.class);

        ArgumentCaptor<NotificationLog> saved = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getAttempts()).isEqualTo(1);
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void recordFailureMarksPendingEntryFailed() {
        NotificationLog pending = new NotificationLog("auth-service", "e1", "EMAIL", "MFA_CODE", "u-1", "a***@example.com", NOW, NOW);
        when(repository.findBySourceAndEventId("auth-service", "e1")).thenReturn(Optional.of(pending));

        recorder.recordFailure(event("e1", "2026-11-03T10:05:00Z", GOOD_DATA), new NotificationSendException("boom", true, null));

        assertThat(pending.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(pending.getFailureReason()).isEqualTo("NotificationSendException");
        verify(repository).save(pending);
    }

    @Test
    void recordFailureStoresRejectedEventWithReasonAndKafkaCoordinates() {
        String promo = event("e9", "2026-11-03T10:05:00Z", GOOD_DATA).replace("MFA_CODE", "NEWSLETTER");
        when(repository.findBySourceAndEventId("auth-service", "e9")).thenReturn(Optional.empty());

        recorder.recordFailure(promo, new InvalidNotificationException("notificationType NEWSLETTER is not accepted"),
                "notification.high-priority", 2, 41L);

        ArgumentCaptor<NotificationLog> saved = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(saved.capture());
        NotificationLog row = saved.getValue();
        assertThat(row.getEventId()).isEqualTo("e9");
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.REJECTED);
        assertThat(row.getFailureReason()).isEqualTo("notificationType NEWSLETTER is not accepted");
        assertThat(row.getNotificationType()).isEqualTo("NEWSLETTER");
        assertThat(row.getSource()).isEqualTo("auth-service");
        assertThat(row.getRecipientUserId()).isEqualTo("u-1");
        assertThat(row.getSourceTopic()).isEqualTo("notification.high-priority");
        assertThat(row.getSourcePartition()).isEqualTo(2);
        assertThat(row.getSourceOffset()).isEqualTo(41L);
    }

    @Test
    void recordFailureForMalformedPayloadKeepsOnlyReasonAndKafkaCoordinates() {
        recorder.recordFailure("{not json", new InvalidNotificationException("Malformed event payload"),
                "notification.high-priority", 0, 7L);

        ArgumentCaptor<NotificationLog> saved = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getEventId()).isNull();
        assertThat(saved.getValue().getSource()).isNull();
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.REJECTED);
        assertThat(saved.getValue().getFailureReason()).isEqualTo("Malformed event payload");
        assertThat(saved.getValue().getSourceOffset()).isEqualTo(7L);
    }

    @Test
    void concurrentDuplicateLosingTheUniqueIndexRaceIsSkippedNotSent() {
        when(repository.findBySourceAndEventId("auth-service", "e1")).thenReturn(Optional.empty());
        when(repository.save(any())).thenThrow(new org.springframework.dao.DuplicateKeyException("dup"));

        service.process(event("e1", "2026-11-03T10:05:00Z", GOOD_DATA));

        verify(sender, never()).send(any());
    }

    @Test
    void recordFailureNeverOverwritesASentEvent() {
        NotificationLog sent = new NotificationLog("auth-service", "e1", "EMAIL", "MFA_CODE", "u-1", "a***@example.com", NOW, NOW);
        sent.markSent(NOW);
        when(repository.findBySourceAndEventId("auth-service", "e1")).thenReturn(Optional.of(sent));

        recorder.recordFailure(event("e1", "2026-11-03T10:05:00Z", GOOD_DATA),
                new InvalidNotificationException("x"), "t", 0, 1L);

        assertThat(sent.getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(repository, never()).save(any());
    }

    @Test
    void recordFailureDoesNotStoreSendErrorMessages() {
        when(repository.findBySourceAndEventId("auth-service", "e1")).thenReturn(Optional.empty());
        var cause = new InvalidNotificationException("Permanent send failure: smtp said 482913",
                new NotificationSendException("smtp said 482913", false, null));

        recorder.recordFailure(event("e1", "2026-11-03T10:05:00Z", GOOD_DATA), cause);

        ArgumentCaptor<NotificationLog> saved = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getFailureReason()).isEqualTo("NotificationSendException");
    }

    @Test
    void nonRetryableSendFailureBecomesInvalidNotification() {
        when(repository.findBySourceAndEventId("auth-service", "e1")).thenReturn(Optional.empty());
        doThrow(new NotificationSendException("rejected", false, null)).when(sender).send(any());

        assertThatThrownBy(() -> service.process(event("e1", "2026-11-03T10:05:00Z", GOOD_DATA)))
                .isInstanceOf(InvalidNotificationException.class)
                .hasMessageContaining("Permanent send failure")
                .hasCauseInstanceOf(NotificationSendException.class);
    }

    @Test
    void pendingRecordIsRetriedAndMarkedSent() {
        NotificationLog pending = new NotificationLog("auth-service", "e1", "EMAIL", "MFA_CODE", "u-1", "a***@example.com", NOW, NOW);
        pending.recordAttempt(NOW);
        when(repository.findBySourceAndEventId("auth-service", "e1")).thenReturn(Optional.of(pending));

        service.process(event("e1", "2026-11-03T10:05:00Z", GOOD_DATA));

        assertThat(pending.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(pending.getAttempts()).isEqualTo(2);
        verify(sender).send(any());
    }

    @Test
    void recordFailureNeverThrowsWhenTheRepositoryIsDown() {
        when(repository.findBySourceAndEventId("auth-service", "e1")).thenThrow(new IllegalStateException("mongo down"));

        recorder.recordFailure(event("e1", "2026-11-03T10:05:00Z", GOOD_DATA), new RuntimeException("x"));

        verify(repository, never()).save(any());
    }

    @Test
    void recordFailureTruncatesLongValidationReasons() {
        when(repository.findBySourceAndEventId("auth-service", "e1")).thenReturn(Optional.empty());

        recorder.recordFailure(event("e1", "2026-11-03T10:05:00Z", GOOD_DATA),
                new InvalidNotificationException("x".repeat(500)));

        ArgumentCaptor<NotificationLog> saved = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getFailureReason()).hasSize(300);
    }
}
