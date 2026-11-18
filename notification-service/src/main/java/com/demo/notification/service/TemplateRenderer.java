package com.demo.notification.service;

import com.demo.notification.event.NotificationEvent;
import com.demo.notification.exception.InvalidNotificationException;
import com.demo.notification.message.EmailMessage;

import java.util.Set;

/**
 * Selects the template for an event by {@code notificationType} AND {@code channel} (the same notification can read
 * differently per channel) and renders it.
 */
public interface TemplateRenderer {

    String EMAIL = "EMAIL";

    String MFA_CODE = "MFA_CODE";
    String OTP = "OTP";
    String FORGOT_PASSWORD = "FORGOT_PASSWORD";
    String VERIFY_EMAIL = "VERIFY_EMAIL";
    String ACCOUNT_BLOCKED = "ACCOUNT_BLOCKED";

    /** The notification types that have at least one template; the parser accepts exactly these. */
    Set<String> types();

    /** @throws InvalidNotificationException for an unknown type/channel or invalid {@code data} */
    void validate(NotificationEvent event);

    EmailMessage renderEmail(NotificationEvent event);
}
