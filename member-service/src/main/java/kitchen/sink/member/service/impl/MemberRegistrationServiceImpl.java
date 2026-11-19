package kitchen.sink.member.service.impl;

import kitchen.sink.member.entity.Member;
import kitchen.sink.member.event.MemberRegisteredEvent;
import kitchen.sink.member.repository.MemberRepository;
import kitchen.sink.member.service.MemberEventParser;
import kitchen.sink.member.service.MemberRegistrationService;
import kitchen.sink.member.service.MemberService;
import kitchen.sink.member.util.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MemberRegistrationServiceImpl implements MemberRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(MemberRegistrationServiceImpl.class);

    private final MemberEventParser parser;
    private final MemberService memberService;

    public MemberRegistrationServiceImpl(MemberEventParser parser, MemberService memberService) {
        this.parser = parser;
        this.memberService = memberService;
    }

    @Override
    public void process(String payload) {
        MemberRegisteredEvent event = parser.parse(payload);
        MemberRegisteredEvent.Data data = event.data();
        Member member = new Member(null, EmailNormalizer.normalize(data.email()), data.phoneNo(),
                data.firstName(), data.lastName());
        if (memberService.createMember(member)) {
            log.info("member {} created from event", data.memberId());
        } else {
            log.info("member {} already exists, event skipped", data.memberId());
        }
    }
}
