package com.demo.notification.service.impl;

import com.demo.notification.service.TemplateRenderer;
import com.demo.notification.service.DeliveryChannel;
import com.demo.notification.service.EmailSender;

import com.demo.notification.event.NotificationEvent;
import com.demo.notification.exception.InvalidNotificationException;
import com.demo.notification.exception.NotificationSendException;
import org.springframework.stereotype.Component;

/** Email delivery: renders the EMAIL template and sends it through the configured {@link EmailSender}. */
@Component
public class EmailChannel implements DeliveryChannel {

    private final TemplateRenderer renderer;
    private final EmailSender sender;

    public EmailChannel(TemplateRenderer renderer, EmailSender sender) {
        this.renderer = renderer;
        this.sender = sender;
    }

    @Override
    public String name() {
        return TemplateRenderer.EMAIL;
    }

    @Override
    public void requireRecipient(NotificationEvent event) {
        String email = event.recipient().email();
        if (email == null || email.isBlank()) {
            throw new InvalidNotificationException("recipient.email is required for channel EMAIL");
        }
    }

    /** {@code a***@example.com} */
    @Override
    public String maskedAddress(NotificationEvent event) {
        String email = event.recipient().email();
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }

    @Override
    public void deliver(NotificationEvent event) throws NotificationSendException {
        sender.send(renderer.renderEmail(event));
    }
}
