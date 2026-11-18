package com.demo.notification.service.impl;

import com.demo.notification.template.DataField;
import com.demo.notification.template.NotificationTemplate;
import com.demo.notification.message.EmailMessage;
import com.demo.notification.service.TemplateRenderer;

import com.demo.notification.event.NotificationEvent;
import com.demo.notification.exception.InvalidNotificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Registry of the notification templates, selected by {@code notificationType} AND {@code channel}: the same
 * notification can read differently per channel (today only EMAIL: subject, greeting, HTML).
 * Texts live in {@code classpath:templates/<TYPE>/EMAIL/{subject.txt,body.txt,body.html}}.
 * To add a template: add its files, register it in {@link #templates()}, document its {@code data}.
 */
@Component
public class TemplateRendererImpl implements TemplateRenderer {

    private static final String CODE = "\\d{4,10}";

    private final String from;
    private final Map<String, NotificationTemplate> templates;

    public TemplateRendererImpl(@Value("${app.mail.from}") String from) {
        this.from = from;
        this.templates = templates().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                t -> key(t.type(), t.channel()), t -> t));
    }

    private static List<NotificationTemplate> templates() {
        List<DataField> mfa = List.of(
                DataField.string("code", true, CODE),
                DataField.positiveInt("validForSeconds"));
        List<DataField> otp = List.of(
                DataField.string("code", true, CODE),
                DataField.positiveInt("validForSeconds"),
                DataField.string("purpose", false, "[\\p{L}\\p{N} .,'-]{1,100}"));
        List<DataField> forgot = List.of(
                DataField.string("resetLink", true, "https?://[^\\s<>\"']{1,2000}"),
                DataField.positiveInt("validForSeconds"));
        List<DataField> verifyEmail = List.of(
                DataField.string("verificationLink", true, "https?://[^\\s<>\"']{1,2000}"),
                DataField.positiveInt("validForSeconds"));
        // warning that the account was blocked after too many wrong passwords; only the delivery deadline is data
        List<DataField> accountBlocked = List.of(DataField.positiveInt("validForSeconds"));
        return List.of(
                email(MFA_CODE, mfa),
                email(OTP, otp),
                email(FORGOT_PASSWORD, forgot),
                email(VERIFY_EMAIL, verifyEmail),
                email(ACCOUNT_BLOCKED, accountBlocked));
    }

    /** The notification types that have at least one template; the parser accepts exactly these. */
    @Override
    public java.util.Set<String> types() {
        return templates.values().stream().map(NotificationTemplate::type)
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
    }

    /** Throws {@link InvalidNotificationException} for an unknown type/channel or invalid {@code data}. */
    @Override
    public void validate(NotificationEvent event) {
        template(event).validate(event.data());
    }

    @Override
    public EmailMessage renderEmail(NotificationEvent event) {
        NotificationTemplate template = template(event);
        template.validate(event.data());
        NotificationTemplate.Rendered r = template.render(event);
        return new EmailMessage(event.eventId(), from, event.recipient().email(), r.subject(), r.text(), r.html());
    }

    private NotificationTemplate template(NotificationEvent event) {
        String type = event.notification().notificationType();
        String channel = event.notification().channel();
        NotificationTemplate template = templates.get(key(type, channel));
        if (template == null) {
            throw new InvalidNotificationException("No template for notificationType " + type + " on channel " + channel);
        }
        return template;
    }

    private static String key(String type, String channel) {
        return type + "/" + channel;
    }

    private static NotificationTemplate email(String type, List<DataField> fields) {
        return new NotificationTemplate(type, EMAIL, fields, read(type, EMAIL, "subject.txt").strip(),
                read(type, EMAIL, "body.txt"), read(type, EMAIL, "body.html"));
    }

    private static String read(String type, String channel, String file) {
        String path = "templates/" + type + "/" + channel + "/" + file;
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing template file " + path, e);
        }
    }
}
