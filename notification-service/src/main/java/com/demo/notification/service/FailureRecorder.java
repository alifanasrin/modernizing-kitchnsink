package com.demo.notification.service;

/** Leaves the audit trail for an event that is about to be dead-lettered. Never stores {@code data}. */
public interface FailureRecorder {

    /** Same as the full overload, for callers without Kafka coordinates. */
    void recordFailure(String payload, Throwable cause);

    /**
     * Called once retries are exhausted or the event is rejected, just before it is dead-lettered: an existing
     * PENDING record becomes FAILED; an event that never got a record gets a REJECTED one with the reason.
     * A record that is already terminal (e.g. SENT) is never overwritten.
     */
    void recordFailure(String payload, Throwable cause, String topic, int partition, long offset);
}
