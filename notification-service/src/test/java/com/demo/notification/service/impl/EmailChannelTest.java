package com.demo.notification.service.impl;

import com.demo.notification.event.NotificationEvent;
import com.demo.notification.exception.InvalidNotificationException;
import com.demo.notification.exception.NotificationSendException;
import com.demo.notification.message.EmailMessage;
import com.demo.notification.service.EmailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailChannelTest {

    @Mock EmailSender sender;

    private EmailChannel channel() {
        return new EmailChannel(new TemplateRendererImpl("no-reply@example.com"), sender);
    }

    private static NotificationEvent event(String email) {
        return new NotificationEvent("e1", "NOTIFICATION_REQUESTED", "1.0", Instant.parse("2026-11-03T09:59:50Z"),
                "auth-service", "corr-1",
                new NotificationEvent.Notification("EMAIL", "MFA_CODE", null, null, null),
                new NotificationEvent.Recipient("u-1", email, "Ann"),
                Map.of("code", "482913", "validForSeconds", 300));
    }

    @Test
    void nameIsEmail() {
        assertThat(channel().name()).isEqualTo("EMAIL");
    }

    @ParameterizedTest
    @CsvSource({"ann@example.com,a***@example.com", "x@y.org,x***@y.org"})
    void masksAllButTheFirstCharacterOfTheLocalPart(String email, String masked) {
        assertThat(channel().maskedAddress(event(email))).isEqualTo(masked);
    }

    @Test
    void masksAddressWithoutLocalPartCompletely() {
        assertThat(channel().maskedAddress(event("@example.com"))).isEqualTo("***");
    }

    @ParameterizedTest
    @CsvSource(value = {"NIL", "''", "'   '"}, nullValues = "NIL")
    void requireRecipientRejectsMissingOrBlankEmail(String email) {
        assertThatThrownBy(() -> channel().requireRecipient(event(email)))
                .isInstanceOf(InvalidNotificationException.class)
                .hasMessageContaining("recipient.email is required");
    }

    @Test
    void requireRecipientAcceptsAnEmail() {
        channel().requireRecipient(event("ann@example.com"));
    }

    @Test
    void deliverRendersTheTemplateAndHandsTheMessageToTheSender() {
        channel().deliver(event("ann@example.com"));

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().to()).isEqualTo("ann@example.com");
        assertThat(sent.getValue().from()).isEqualTo("no-reply@example.com");
        assertThat(sent.getValue().eventId()).isEqualTo("e1");
        assertThat(sent.getValue().textBody()).contains("482913");
    }

    @Test
    void deliverPropagatesSenderFailures() {
        doThrow(new NotificationSendException("down", true, null)).when(sender).send(any());

        assertThatThrownBy(() -> channel().deliver(event("ann@example.com")))
                .isInstanceOf(NotificationSendException.class);
    }
}
