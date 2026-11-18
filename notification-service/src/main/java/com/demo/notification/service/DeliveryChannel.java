package com.demo.notification.service;

import com.demo.notification.event.NotificationEvent;
import com.demo.notification.exception.InvalidNotificationException;
import com.demo.notification.exception.NotificationSendException;

/**
 * One way of reaching a recipient (email today; push, WhatsApp, ... later). {@link NotificationService} knows only this interface, so a
 * new channel (push, WhatsApp) is a new implementation, with no change to the consumer flow.
 */
public interface DeliveryChannel {

    /** The {@code notification.channel} value this implementation handles, e.g. {@code EMAIL}. */
    String name();

    /** @throws InvalidNotificationException the recipient lacks the address this channel needs */
    void requireRecipient(NotificationEvent event);

    /** The recipient address with its middle hidden; this is the only form that is stored. */
    String maskedAddress(NotificationEvent event);

    /** Renders the channel's template and hands the message to the provider. */
    void deliver(NotificationEvent event) throws NotificationSendException;
}
