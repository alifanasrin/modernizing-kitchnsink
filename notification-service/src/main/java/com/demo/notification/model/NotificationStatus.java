package com.demo.notification.model;

public enum NotificationStatus {
    /** PENDING -> SENT | FAILED | EXPIRED; REJECTED = never accepted (malformed, unsupported type/channel/version, bad data). */
    PENDING, SENT, FAILED, EXPIRED, REJECTED
}
