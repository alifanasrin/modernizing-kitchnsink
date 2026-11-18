package com.demo.notification.service.impl;

import com.demo.notification.exception.InvalidNotificationException;
import com.demo.notification.service.EmailSender;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class NotificationEventParserImplTest {

    @Mock EmailSender sender;

    NotificationEventParserImpl parser;

    @BeforeEach
    void setUp() {
        var renderer = new TemplateRendererImpl("no-reply@example.com");
        parser = new NotificationEventParserImpl(JsonMapper.builder().build(),
                Validation.buildDefaultValidatorFactory().getValidator(), renderer,
                List.of(new EmailChannel(renderer, sender)));
    }

    private static String payload(String eventType, String eventId) {
        return """
                {"eventId":%s,"eventType":"%s","schemaVersion":"1.0",
                 "occurredAt":"2026-11-03T09:59:50Z","source":"auth-service","correlationId":"corr-1",
                 "notification":{"channel":"EMAIL","notificationType":"MFA_CODE"},
                 "recipient":{"userId":"u-1","email":"ann@example.com"},
                 "data":{"code":"482913","validForSeconds":300}}"""
                .formatted(eventId, eventType);
    }

    @Test
    @Tag("critical")
    void shouldReturnEvent_whenPayloadIsValid() {
        var event = parser.parse(payload("NOTIFICATION_REQUESTED", "\"e1\""));

        assertThat(event.eventId()).isEqualTo("e1");
        assertThat(parser.channelOf(event).name()).isEqualTo("EMAIL");
    }

    @Test
    @Tag("critical")
    void shouldRejectWithSortedViolations_whenRequiredFieldsAreMissing() {
        assertThatThrownBy(() -> parser.parse(payload("NOTIFICATION_REQUESTED", "\"\"")))
                .isInstanceOf(InvalidNotificationException.class)
                .hasMessage("Invalid event: eventId must not be blank");
    }

    @Test
    void shouldListEveryViolationAlphabetically_whenSeveralFieldsAreInvalid() {
        String json = """
                {"eventId":"","eventType":"NOTIFICATION_REQUESTED","schemaVersion":"1.0",
                 "occurredAt":"2026-11-03T09:59:50Z","source":"","correlationId":"c",
                 "notification":{"channel":"EMAIL","notificationType":"MFA_CODE"},
                 "recipient":{"userId":"u-1"},"data":{}}""";

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(InvalidNotificationException.class)
                .hasMessage("Invalid event: eventId must not be blank, source must not be blank");
    }

    @Test
    void shouldRejectWithoutEchoingValues_whenEmailAddressIsInvalid() {
        String json = payload("NOTIFICATION_REQUESTED", "\"e1\"").replace("ann@example.com", "not-an-email");

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(InvalidNotificationException.class)
                .hasMessageContaining("recipient.email")
                .hasMessageNotContaining("not-an-email");
    }

    @Test
    @Tag("critical")
    void shouldRejectUnsupportedEventType() {
        assertThatThrownBy(() -> parser.parse(payload("SOMETHING_ELSE", "\"e1\"")))
                .isInstanceOf(InvalidNotificationException.class)
                .hasMessage("Unsupported eventType: SOMETHING_ELSE");
    }

    @Test
    void shouldRejectEventType_whenCaseDiffers() {
        assertThatThrownBy(() -> parser.parse(payload("notification_requested", "\"e1\"")))
                .isInstanceOf(InvalidNotificationException.class)
                .hasMessage("Unsupported eventType: notification_requested");
    }
}
