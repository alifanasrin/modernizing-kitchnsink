package kitchen.sink.demo.service.impl;

import kitchen.sink.demo.event.MemberRegistered;
import kitchen.sink.demo.event.MemberRegisteredMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.UUID;

/**
 * Publishes {@link MemberRegistered} to Kafka for member-service, after the registration has committed (so a
 * rolled-back registration never produces an event). {@code fallbackExecution} keeps it working when there is no
 * transaction (local MongoDB without a replica set).
 *
 * <p>The send is fire-and-forget: a Kafka outage never fails a registration that is already saved, it is logged at
 * ERROR. Known gap: without a transactional outbox such a member is not created in member-service until it is
 * replayed by hand.
 */
@Component
public class KafkaMemberEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaMemberEventPublisher.class);

    private final KafkaOperations<String, String> kafka;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final String topic;

    public KafkaMemberEventPublisher(KafkaOperations<String, String> kafka, JsonMapper jsonMapper, Clock clock,
                                     @Value("${app.kafka.topic.member-registered}") String topic) {
        this.kafka = kafka;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMemberRegistered(MemberRegistered registered) {
        var message = new MemberRegisteredMessage(UUID.randomUUID().toString(), MemberRegisteredMessage.EVENT_TYPE,
                MemberRegisteredMessage.SCHEMA_VERSION, MemberRegisteredMessage.SOURCE, clock.instant(),
                new MemberRegisteredMessage.Data(registered.memberId(), registered.email(), registered.phoneNo(),
                        registered.firstName(), registered.lastName()));
        try {
            // keyed by the member id: all events of one member stay ordered on one partition, and no PII is in the key
            kafka.send(topic, registered.memberId(), jsonMapper.writeValueAsString(message))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("member-registered event {} for member {} was not published",
                                    message.eventId(), registered.memberId(), error);
                        } else {
                            log.info("member-registered event {} for member {} published to partition {}",
                                    message.eventId(), registered.memberId(), result.getRecordMetadata().partition());
                        }
                    });
        } catch (RuntimeException e) {
            log.error("member-registered event {} for member {} was not published",
                    message.eventId(), registered.memberId(), e);
        }
    }
}
