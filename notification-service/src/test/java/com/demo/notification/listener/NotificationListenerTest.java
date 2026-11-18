package com.demo.notification.listener;

import com.demo.notification.exception.InvalidNotificationException;
import com.demo.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock NotificationService service;

    @Test
    void delegatesThePayloadToTheService() {
        new NotificationListener(service).onMessage("{\"a\":1}");

        verify(service).process("{\"a\":1}");
    }

    @Test
    void letsFailuresReachTheErrorHandler() {
        doThrow(new InvalidNotificationException("bad")).when(service).process("x");

        assertThatThrownBy(() -> new NotificationListener(service).onMessage("x"))
                .isInstanceOf(InvalidNotificationException.class);
    }
}
