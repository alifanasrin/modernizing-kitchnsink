package com.demo.notification.service;

import com.demo.notification.message.EmailMessage;

import com.demo.notification.exception.NotificationSendException;

/** Delivery boundary. Only a mock exists today; a real provider is another implementation of this interface. */
public interface EmailSender {
    void send(EmailMessage message) throws NotificationSendException;
}
