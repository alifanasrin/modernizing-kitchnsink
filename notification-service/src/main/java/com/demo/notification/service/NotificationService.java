package com.demo.notification.service;

import com.demo.notification.exception.InvalidNotificationException;
import com.demo.notification.exception.NotificationSendException;

/** Handles one {@code notification.requests} message from the listener. */
public interface NotificationService {

    /**
     * Parses, de-duplicates, delivers and records one message.
     * @throws InvalidNotificationException the event can never succeed (not retried)
     * @throws NotificationSendException    a transient send failure (retried by the listener's error handler)
     */
    void process(String payload);
}
