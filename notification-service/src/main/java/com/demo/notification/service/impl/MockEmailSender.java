package com.demo.notification.service.impl;

import com.demo.notification.message.EmailMessage;
import com.demo.notification.service.EmailSender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local-development sender: nothing is sent, the whole email (including the MFA code) is logged.
 * Do not use outside local development.
 */
@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "mock", matchIfMissing = true)
public class MockEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(MockEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info("""
                [MOCK EMAIL] eventId={}
                From:    {}
                To:      {}
                Subject: {}
                ----------------------------------------
                {}
                ----------------------------------------""",
                message.eventId(), message.from(), message.to(), message.subject(), message.textBody());
    }
}
