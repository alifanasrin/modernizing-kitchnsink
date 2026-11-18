package com.demo.notification.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One document per notification event. {@code _id} is generated here; the producer's {@code eventId} is a plain
 * field, made unique per producer by the index on ({@code source}, {@code eventId}), so a redelivered event cannot
 * create a second record and two producers may reuse the same id value. Records live for
 * {@code app.log.retention} (default 24h) after creation (TTL index), which is also the de-duplication window.
 * Never holds the MFA code or any other event {@code data}.
 */
@Document(collection = "notification_log")
@CompoundIndex(name = "uk_source_event_id", def = "{ 'source': 1, 'eventId': 1 }", unique = true,
        partialFilter = "{ 'source': { $exists: true }, 'eventId': { $exists: true } }")
public class NotificationLog {

    @Id
    private String id;
    /** Producing service name from the envelope. */
    private String source;
    /** Producer-supplied idempotency key; absent on rejected records whose payload had none. */
    private String eventId;
    private String channel;
    private String notificationType;
    private String recipientUserId;
    /** Masked, e.g. {@code a***@example.com}. */
    private String recipientAddress;
    private NotificationStatus status;
    private int attempts;
    private String failureReason;
    private Instant requestedAt;
    @Indexed(name = "ttl_created_at", expireAfter = "${app.log.retention:24h}")
    private Instant createdAt;
    private Instant sentAt;
    private Instant updatedAt;
    /** Where the dead-lettered record sits, so a FAILED/REJECTED event can be found on the DLT. Null if not dead-lettered. */
    private String sourceTopic;
    private Integer sourcePartition;
    private Long sourceOffset;

    protected NotificationLog() {
    }

    public NotificationLog(String source, String eventId, String channel, String notificationType,
                           String recipientUserId, String recipientAddress, Instant requestedAt, Instant now) {
        this.source = source;
        this.eventId = eventId;
        this.channel = channel;
        this.notificationType = notificationType;
        this.recipientUserId = recipientUserId;
        this.recipientAddress = recipientAddress;
        this.requestedAt = requestedAt;
        this.createdAt = now;
        this.updatedAt = now;
        this.status = NotificationStatus.PENDING;
    }

    public void markSent(Instant now) {
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
        this.updatedAt = now;
        this.failureReason = null;
    }

    public void markFailed(String reason, Instant now) {
        this.status = NotificationStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = now;
    }

    /** A record for an event that was refused before any attempt; {@code eventId} may be a synthetic Kafka id. */
    public static NotificationLog rejected(String source, String eventId, String channel, String notificationType,
                                           String recipientUserId, String reason, Instant now) {
        NotificationLog log = new NotificationLog(source, eventId, channel, notificationType, recipientUserId,
                null, null, now);
        log.status = NotificationStatus.REJECTED;
        log.failureReason = reason;
        return log;
    }

    public void markRejected(String reason, Instant now) {
        this.status = NotificationStatus.REJECTED;
        this.failureReason = reason;
        this.updatedAt = now;
    }

    public void recordDeadLetter(String topic, int partition, long offset) {
        this.sourceTopic = topic;
        this.sourcePartition = partition;
        this.sourceOffset = offset;
    }

    public void markExpired(Instant now) {
        this.status = NotificationStatus.EXPIRED;
        this.updatedAt = now;
    }

    public void recordAttempt(Instant now) {
        this.attempts++;
        this.updatedAt = now;
    }

    public String getId() { return id; }
    public String getSource() { return source; }
    public String getEventId() { return eventId; }
    public String getChannel() { return channel; }
    public String getNotificationType() { return notificationType; }
    public String getRecipientUserId() { return recipientUserId; }
    public String getRecipientAddress() { return recipientAddress; }
    public NotificationStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getFailureReason() { return failureReason; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getSourceTopic() { return sourceTopic; }
    public Integer getSourcePartition() { return sourcePartition; }
    public Long getSourceOffset() { return sourceOffset; }
}
