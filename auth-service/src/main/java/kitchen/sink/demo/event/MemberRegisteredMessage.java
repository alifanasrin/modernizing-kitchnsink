package kitchen.sink.demo.event;

import java.time.Instant;

/**
 * The Kafka message on the member-registered topic (contract v1.0, also read by member-service). Additive changes
 * bump the minor {@code schemaVersion}; the consumer accepts any {@code 1.x}.
 */
public record MemberRegisteredMessage(String eventId, String eventType, String schemaVersion, String source,
                                      Instant occurredAt, Data data) {

    public static final String EVENT_TYPE = "MEMBER_REGISTERED";
    public static final String SCHEMA_VERSION = "1.0";
    public static final String SOURCE = "auth-service";

    /** The member as registered; never the password or its hash. */
    public record Data(String memberId, String email, String phoneNo, String firstName, String lastName) {
        @Override
        public String toString() {
            return "Data[memberId=" + memberId + "]"; // names, email and phone are PII
        }
    }
}
