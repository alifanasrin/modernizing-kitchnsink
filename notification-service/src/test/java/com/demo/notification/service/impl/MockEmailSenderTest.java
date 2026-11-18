package com.demo.notification.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.demo.notification.message.EmailMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class MockEmailSenderTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(MockEmailSender.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    @Test
    void logsTheWholeEmailInsteadOfSendingIt() {
        new MockEmailSender().send(new EmailMessage("e1", "no-reply@example.com", "ann@example.com",
                "Your code", "Code: 482913", "<p>Code: 482913</p>"));

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("[MOCK EMAIL] eventId=e1", "no-reply@example.com", "ann@example.com",
                        "Your code", "Code: 482913");
    }
}
