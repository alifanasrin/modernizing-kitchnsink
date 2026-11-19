package kitchen.sink.member.service.impl;

import kitchen.sink.member.entity.Member;
import kitchen.sink.member.event.MemberRegisteredEvent;
import kitchen.sink.member.repository.MemberStore;
import kitchen.sink.member.service.MemberEventParser;
import kitchen.sink.member.service.MemberRegistrationService;
import kitchen.sink.member.util.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MemberRegistrationServiceImpl implements MemberRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(MemberRegistrationServiceImpl.class);

    private final MemberEventParser parser;
    private final MemberStore memberStore;

    public MemberRegistrationServiceImpl(MemberEventParser parser, MemberStore memberStore) {
        this.parser = parser;
        this.memberStore = memberStore;
    }

    @Override
    public void process(String payload) {
        MemberRegisteredEvent event = parser.parse(payload);
        MemberRegisteredEvent.Data data = event.data();
        Member member = new Member(null, EmailNormalizer.normalize(data.email()), data.phoneNo(),
                data.firstName(), data.lastName());
        if (memberStore.createIfAbsent(member)) {
            log.info("member {} created from event {}", data.memberId(), event.eventId());
        } else {
            log.info("member {} already exists, event {} skipped", data.memberId(), event.eventId());
        }
    }
}
