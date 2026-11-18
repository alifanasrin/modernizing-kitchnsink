package com.demo.notification.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

/** Envelope of a {@code notification.high-priority} message; see docs/event-payload-design.md. */
public record NotificationEvent(
        @NotBlank String eventId,
        @NotBlank String eventType,
        @NotBlank String schemaVersion,
        @NotNull Instant occurredAt,
        @NotBlank String source,
        @NotBlank String correlationId,
        @NotNull @Valid Notification notification,
        @NotNull @Valid Recipient recipient,
        @NotNull Map<String, Object> data) {

    public static final String EVENT_TYPE = "NOTIFICATION_REQUESTED";
    public static final String SUPPORTED_MAJOR_VERSION = "1";

    public record Notification(
            @NotBlank String channel,
            @NotBlank String notificationType,
            String priority,
            Instant expiresAt,
            String locale) {
    }

    public record Recipient(
            @NotBlank String userId,
            @Email String email,
            String name) {
    }

    /** Never print {@code data}: it holds the MFA code. */
    @Override
    public String toString() {
        return "NotificationEvent[eventId=" + eventId + ", correlationId=" + correlationId
                + ", type=" + (notification == null ? null : notification.notificationType()) + "]";
    }
}
