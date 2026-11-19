package kitchen.sink.demo.event;

/**
 * The Kafka message on the member-registered topic (also read by member-service). The consumer ignores unknown
 * fields, so additive changes are safe.
 */
public record MemberRegisteredMessage(Data data) {

    /** The member as registered; never the password or its hash. */
    public record Data(String memberId, String email, String phoneNo, String firstName, String lastName) {
        @Override
        public String toString() {
            return "Data[memberId=" + memberId + "]"; // names, email and phone are PII
        }
    }
}
