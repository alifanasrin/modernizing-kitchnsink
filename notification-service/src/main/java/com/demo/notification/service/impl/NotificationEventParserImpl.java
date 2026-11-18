package com.demo.notification.service.impl;

import com.demo.notification.service.NotificationEventParser;
import com.demo.notification.service.TemplateRenderer;
import com.demo.notification.service.DeliveryChannel;

import com.demo.notification.event.NotificationEvent;
import com.demo.notification.exception.InvalidNotificationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns a raw payload into a trusted {@link NotificationEvent}, or rejects it with an
 * {@link InvalidNotificationException} whose message never contains event values.
 */
@Component
public class NotificationEventParserImpl implements NotificationEventParser {

    private final JsonMapper jsonMapper;
    private final Validator validator;
    private final TemplateRenderer renderer;
    private final Map<String, DeliveryChannel> channels;

    public NotificationEventParserImpl(JsonMapper jsonMapper, Validator validator, TemplateRenderer renderer,
                                   List<DeliveryChannel> channels) {
        this.jsonMapper = jsonMapper;
        this.validator = validator;
        this.renderer = renderer;
        this.channels = channels.stream().collect(Collectors.toUnmodifiableMap(DeliveryChannel::name, Function.identity()));
    }

    @Override
    public NotificationEvent parse(String payload) {
        NotificationEvent event;
        try {
            event = jsonMapper.readValue(payload, NotificationEvent.class);
        } catch (RuntimeException e) {
            throw new InvalidNotificationException("Malformed event payload", e);
        }
        Set<ConstraintViolation<NotificationEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new InvalidNotificationException("Invalid event: " + violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().collect(Collectors.joining(", ")));
        }
        if (!NotificationEvent.EVENT_TYPE.equals(event.eventType())) {
            throw new InvalidNotificationException("Unsupported eventType: " + event.eventType());
        }
        if (!event.schemaVersion().startsWith(NotificationEvent.SUPPORTED_MAJOR_VERSION + ".")) {
            throw new InvalidNotificationException("Unsupported schemaVersion: " + event.schemaVersion());
        }
        String channel = event.notification().channel();
        DeliveryChannel deliveryChannel = channels.get(channel);
        if (deliveryChannel == null) {
            throw new InvalidNotificationException("Unsupported channel: " + channel);
        }
        deliveryChannel.requireRecipient(event);
        String type = event.notification().notificationType();
        Set<String> accepted = renderer.types(); // a new template is accepted without touching this class
        if (!accepted.contains(type)) {
            throw new InvalidNotificationException("notificationType " + type
                    + " is not accepted on the requests topic; allowed types: " + accepted);
        }
        renderer.validate(event); // unknown template or bad data: reject before any record is created
        return event;
    }

    /** The channel that delivers an event {@link #parse} accepted. */
    @Override
    public DeliveryChannel channelOf(NotificationEvent event) {
        return channels.get(event.notification().channel());
    }
}
