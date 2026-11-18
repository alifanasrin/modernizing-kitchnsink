package com.demo.notification.exception;

/** The event can never be processed (bad shape, unsupported type/channel/version). Not retried; goes to the DLT. */
public class InvalidNotificationException extends RuntimeException {
    public InvalidNotificationException(String message) {
        super(message);
    }

    public InvalidNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
