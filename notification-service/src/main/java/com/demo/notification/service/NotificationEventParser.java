package com.demo.notification.service;

import com.demo.notification.event.NotificationEvent;
import com.demo.notification.exception.InvalidNotificationException;

/**
 * Turns a raw payload into a trusted {@link NotificationEvent}, or rejects it with an
 * {@link InvalidNotificationException} whose message never contains event values.
 */
public interface NotificationEventParser {

    NotificationEvent parse(String payload);

    /** The channel that delivers an event {@link #parse} accepted. */
    DeliveryChannel channelOf(NotificationEvent event);
}
