package kitchen.sink.member.listener;

import kitchen.sink.member.service.MemberRegistrationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Entry point of the member-registered events; all work is in {@link MemberRegistrationService}. */
@Component
public class MemberRegisteredListener {

    private final MemberRegistrationService registrationService;

    public MemberRegisteredListener(MemberRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @KafkaListener(topics = "${app.kafka.topic.member-registered}")
    public void onMessage(String payload) {
        registrationService.process(payload);
    }
}
