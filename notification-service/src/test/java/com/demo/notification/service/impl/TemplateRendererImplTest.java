package com.demo.notification.service.impl;

import com.demo.notification.service.TemplateRenderer;

import com.demo.notification.message.EmailMessage;

import com.demo.notification.exception.InvalidNotificationException;
import com.demo.notification.event.NotificationEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateRendererImplTest {

    final TemplateRenderer renderer = new TemplateRendererImpl("no-reply@example.com");

    static NotificationEvent event(String type, String name, Map<String, Object> data) {
        return event("EMAIL", type, name, data);
    }

    static NotificationEvent event(String channel, String type, String name, Map<String, Object> data) {
        return new NotificationEvent("e1", NotificationEvent.EVENT_TYPE, "1.0", Instant.now(), "auth-service", "c1",
                new NotificationEvent.Notification(channel, type, null, null, null),
                new NotificationEvent.Recipient("u-1", "ann@example.com", name), data);
    }

    private static void assertFullyRendered(EmailMessage m) {
        assertThat(m.subject() + m.textBody() + m.htmlBody()).doesNotContain("{{");
    }

    @Test
    @Tag("critical")
    void mfaCode_rendersCodeAndMinutes() {
        EmailMessage m = renderer.renderEmail(event("MFA_CODE", "Ann", Map.of("code", "482913", "validForSeconds", 300)));

        assertThat(m.subject()).isEqualTo("Your verification code");
        assertThat(m.textBody()).contains("Hi Ann,").contains("482913").contains("5 minute(s)");
        assertThat(m.htmlBody()).contains("<strong>482913</strong>");
        assertThat(m.to()).isEqualTo("ann@example.com");
        assertThat(m.from()).isEqualTo("no-reply@example.com");
        assertFullyRendered(m);
    }

    @Test
    @Tag("critical")
    void otp_usesPurposeOrDefault() {
        EmailMessage withPurpose = renderer.renderEmail(event("OTP", null,
                Map.of("code", "123456", "validForSeconds", 600, "purpose", "verify your email address")));
        EmailMessage without = renderer.renderEmail(event("OTP", null, Map.of("code", "123456", "validForSeconds", 600)));

        assertThat(withPurpose.subject()).isEqualTo("Your one-time passcode");
        assertThat(withPurpose.textBody()).contains("Hi there,").contains("verify your email address")
                .contains("10 minute(s)");
        assertThat(without.textBody()).contains("verify your identity");
        assertFullyRendered(withPurpose);
        assertFullyRendered(without);
    }

    @Test
    @Tag("critical")
    void forgotPassword_rendersResetLink() {
        EmailMessage m = renderer.renderEmail(event("FORGOT_PASSWORD", "Ann",
                Map.of("resetLink", "https://app.example.com/reset?token=abc123", "validForSeconds", 1800)));

        assertThat(m.subject()).isEqualTo("Reset your password");
        assertThat(m.textBody()).contains("https://app.example.com/reset?token=abc123").contains("30 minute(s)");
        assertThat(m.htmlBody()).contains("href=\"https://app.example.com/reset?token=abc123\"");
        assertFullyRendered(m);
    }

    @Test
    @Tag("critical")
    void verifyEmail_rendersVerificationLink() {
        EmailMessage m = renderer.renderEmail(event("VERIFY_EMAIL", "Ann",
                Map.of("verificationLink", "https://app.example.com/verify-email?token=ABCD2345.xyz", "validForSeconds", 86400)));

        assertThat(m.subject()).isEqualTo("Verify your email address");
        assertThat(m.textBody()).contains("https://app.example.com/verify-email?token=ABCD2345.xyz")
                .contains("1440 minute(s)").contains("Hi Ann");
        assertThat(m.htmlBody()).contains("href=\"https://app.example.com/verify-email?token=ABCD2345.xyz\"");
        assertFullyRendered(m);
    }

    @Test
    @Tag("critical")
    void accountBlocked_warnsAndAsksTheMemberToResetThePassword() {
        EmailMessage m = renderer.renderEmail(event("ACCOUNT_BLOCKED", "Ann", Map.of("validForSeconds", 86400)));

        assertThat(m.subject()).isEqualTo("Your account has been blocked");
        assertThat(m.textBody()).contains("Hi Ann").contains("too many incorrect password attempts")
                .contains("reset your password");
        assertThat(m.htmlBody()).contains("reset your password");
        assertFullyRendered(m);
    }

    @Test
    void verifyEmail_rejectsUnsafeLink() {
        assertThatThrownBy(() -> renderer.validate(event("VERIFY_EMAIL", null,
                Map.of("verificationLink", "javascript:alert(1)", "validForSeconds", 60))))
                .hasMessage("VERIFY_EMAIL has an invalid data.verificationLink");
    }

    @Test
    @Tag("critical")
    void htmlBodyEscapesValues_textBodyDoesNot() {
        EmailMessage m = renderer.renderEmail(event("MFA_CODE", "<b>Ann</b>", Map.of("code", "482913", "validForSeconds", 300)));

        assertThat(m.htmlBody()).contains("&lt;b&gt;Ann&lt;/b&gt;").doesNotContain("<b>Ann");
        assertThat(m.textBody()).contains("<b>Ann</b>");
    }

    @Test
    void valuesAreNotRescannedForPlaceholders() {
        EmailMessage m = renderer.renderEmail(event("MFA_CODE", "{{code}}", Map.of("code", "482913", "validForSeconds", 300)));

        assertThat(m.textBody()).contains("Hi {{code}},");
    }

    @Test
    @Tag("critical")
    void rejectsUnknownTemplate() {
        assertThatThrownBy(() -> renderer.validate(event("PROMO", "Ann", Map.of())))
                .isInstanceOf(InvalidNotificationException.class).hasMessageContaining("PROMO");
    }

    @Test
    @Tag("critical")
    void rejectsInvalidData_withoutEchoingValues() {
        assertThatThrownBy(() -> renderer.validate(event("MFA_CODE", null, Map.of("code", "12ab", "validForSeconds", 300))))
                .isInstanceOf(InvalidNotificationException.class).hasMessage("MFA_CODE has an invalid data.code");
        assertThatThrownBy(() -> renderer.validate(event("MFA_CODE", null, Map.of("code", "482913"))))
                .hasMessage("MFA_CODE requires data.validForSeconds");
        assertThatThrownBy(() -> renderer.validate(event("MFA_CODE", null, Map.of("code", "482913", "validForSeconds", 0))))
                .hasMessage("MFA_CODE has an invalid data.validForSeconds");
        assertThatThrownBy(() -> renderer.validate(event("FORGOT_PASSWORD", null,
                Map.of("resetLink", "javascript:alert(1)", "validForSeconds", 60))))
                .hasMessage("FORGOT_PASSWORD has an invalid data.resetLink");
        assertThatThrownBy(() -> renderer.validate(event("OTP", null,
                Map.of("code", "123456", "validForSeconds", 60, "purpose", "<script>"))))
                .hasMessage("OTP has an invalid data.purpose");
    }

    @Test
    void rejectsChannelWithoutTemplate() {
        assertThatThrownBy(() -> renderer.validate(event("PUSH", "MFA_CODE", null, Map.of("code", "482913", "validForSeconds", 60))))
                .isInstanceOf(InvalidNotificationException.class).hasMessageContaining("PUSH");
    }
}
