package com.demo.notification.service.impl;

import com.demo.notification.service.FailureRecorder;

import com.demo.notification.exception.InvalidNotificationException;
import com.demo.notification.exception.NotificationSendException;
import com.demo.notification.model.NotificationLog;
import com.demo.notification.model.NotificationStatus;
import com.demo.notification.repository.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;

/** Leaves the audit trail for an event that is about to be dead-lettered. Never stores {@code data}. */
@Component
public class FailureRecorderImpl implements FailureRecorder {

    private static final Logger log = LoggerFactory.getLogger(FailureRecorderImpl.class);

    private final JsonMapper jsonMapper;
    private final NotificationLogRepository repository;
    private final Clock clock;

    public FailureRecorderImpl(JsonMapper jsonMapper, NotificationLogRepository repository, Clock clock) {
        this.jsonMapper = jsonMapper;
        this.repository = repository;
        this.clock = clock;
    }

    /** Same as the full overload, for callers without Kafka coordinates. */
    @Override
    public void recordFailure(String payload, Throwable cause) {
        recordFailure(payload, cause, null, -1, -1);
    }

    /**
     * Called once retries are exhausted or the event is rejected, just before it is dead-lettered. Always leaves a
     * trace in {@code notification_log}: an existing PENDING record becomes FAILED; an event that never got a
     * record (malformed, unsupported type/channel/version, bad data) gets a REJECTED one with the reason; when the
     * payload has no usable source/eventId the record carries only the reason and the Kafka coordinates.
     * Only the reason and a few identifiers are stored, never {@code data}.
     */
    @Override
    public void recordFailure(String payload, Throwable cause, String topic, int partition, long offset) {
        try {
            Instant now = clock.instant();
            JsonNode root = readTreeOrNull(payload);
            String eventId = text(root, "eventId");
            String source = text(root, "source");
            NotificationLog entry = eventId == null || source == null ? null
                    : repository.findBySourceAndEventId(source, eventId).orElse(null);
            if (entry == null) {
                JsonNode n = root == null ? null : root.path("notification");
                JsonNode r = root == null ? null : root.path("recipient");
                entry = NotificationLog.rejected(source, eventId, text(n, "channel"),
                        text(n, "notificationType"), text(r, "userId"), reason(cause), now);
            } else if (entry.getStatus() == NotificationStatus.PENDING) {
                entry.markFailed(reason(cause), now);
            } else {
                return; // already terminal (e.g. SENT): a later bad redelivery must not overwrite it
            }
            if (topic != null) {
                entry.recordDeadLetter(topic, partition, offset);
            }
            repository.save(entry);
            log.warn("event {} {} ({})", eventId, entry.getStatus(), entry.getFailureReason());
        } catch (RuntimeException e) {
            log.warn("could not record failure: {}", e.getMessage());
        }
    }

    private JsonNode readTreeOrNull(String payload) {
        try {
            return jsonMapper.readTree(payload);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A short textual field, or null; capped so a hostile payload cannot bloat the record. */
    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.path(field);
        if (!v.isString() || v.asString().isBlank()) {
            return null;
        }
        String s = v.asString();
        return s.length() > 100 ? s.substring(0, 100) : s;
    }

    /**
     * The stored failure reason. Validation messages are written without event values, so they are kept as is;
     * anything else (send errors can echo content) is reduced to the exception class name.
     */
    private static String reason(Throwable cause) {
        if (cause instanceof InvalidNotificationException e && !(e.getCause() instanceof NotificationSendException)) {
            String m = e.getMessage();
            return m.length() > 300 ? m.substring(0, 300) : m;
        }
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }
}
