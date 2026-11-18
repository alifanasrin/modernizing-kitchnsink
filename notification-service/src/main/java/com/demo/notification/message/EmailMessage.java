package com.demo.notification.message;

public record EmailMessage(String eventId, String from, String to, String subject, String textBody, String htmlBody) {
}
