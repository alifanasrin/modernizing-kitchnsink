package kitchen.sink.demo.event;

/**
 * In-process event: a member was just registered. Carries no credential, so nothing downstream can leak one.
 * {@code KafkaMemberEventPublisher} turns it into the Kafka message once the registration is committed.
 */
public record MemberRegistered(String memberId, String email, String phoneNo, String firstName, String lastName) {
}
