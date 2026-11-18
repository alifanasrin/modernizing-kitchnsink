package com.demo.notification.service.impl;

import com.demo.notification.service.NotificationService;
import com.demo.notification.service.FailureRecorder;
import com.demo.notification.service.NotificationEventParser;
import com.demo.notification.service.DeliveryChannel;

import com.demo.notification.event.NotificationEvent;
import com.demo.notification.exception.InvalidNotificationException;
import com.demo.notification.exception.NotificationSendException;
import com.demo.notification.model.NotificationLog;
import com.demo.notification.model.NotificationStatus;
import com.demo.notification.repository.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Handles one {@code notification.requests} message: parse, de-duplicate, skip if expired, deliver through the
 * event's {@link DeliveryChannel}, and record the outcome. Parsing lives in {@link NotificationEventParser} and the
 * dead-letter audit in {@link FailureRecorder}. Never logs {@code data} (it holds the MFA code).
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationEventParser parser;
    private final NotificationLogRepository repository;
    private final Clock clock;

    public NotificationServiceImpl(NotificationEventParser parser, NotificationLogRepository repository, Clock clock) {
        this.parser = parser;
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * @throws InvalidNotificationException the event can never succeed (not retried)
     * @throws NotificationSendException           a transient send failure (retried by the listener's error handler)
     */
    @Override
    public void process(String payload) {
        NotificationEvent event = parser.parse(payload);
        DeliveryChannel channel = parser.channelOf(event);
        Instant now = clock.instant();

        NotificationLog entry = repository.findBySourceAndEventId(event.source(), event.eventId()).orElse(null);
        if (entry != null && entry.getStatus() != NotificationStatus.PENDING) {
            log.info("duplicate event {} already {}, skipping", event.eventId(), entry.getStatus());
            return;
        }
        if (entry == null) {
            entry = new NotificationLog(event.source(), event.eventId(), event.notification().channel(),
                    event.notification().notificationType(), event.recipient().userId(),
                    channel.maskedAddress(event), event.occurredAt(), now);
        }

        Instant expiresAt = event.notification().expiresAt();
        if (expiresAt != null && !now.isBefore(expiresAt)) {
            entry.markExpired(now);
            if (!persist(entry)) {
                return;
            }
            log.warn("event {} expired at {}, not sent", event.eventId(), expiresAt);
            return;
        }

        entry.recordAttempt(now);
        if (!persist(entry)) {
            return;
        }
        try {
            channel.deliver(event);
        } catch (NotificationSendException e) {
            if (e.isRetryable()) {
                log.warn("event {} send attempt {} failed: {}", event.eventId(), entry.getAttempts(), e.getMessage());
                throw e;
            }
            throw new InvalidNotificationException("Permanent send failure: " + e.getMessage(), e);
        }
        entry.markSent(clock.instant());
        repository.save(entry);
        log.info("event {} ({}) sent, correlationId={}", event.eventId(),
                event.notification().notificationType(), event.correlationId());
    }

    /**
     * Saves the record. A new record that loses the race against the unique (source, eventId) index means another
     * consumer already took this event: false, the caller skips it.
     */
    private boolean persist(NotificationLog entry) {
        try {
            repository.save(entry);
            return true;
        } catch (DuplicateKeyException e) {
            log.info("duplicate event {} recorded concurrently, skipping", entry.getEventId());
            return false;
        }
    }
}
