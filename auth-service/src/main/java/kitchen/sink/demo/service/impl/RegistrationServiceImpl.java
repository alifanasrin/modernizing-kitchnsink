package kitchen.sink.demo.service.impl;

import kitchen.sink.demo.dto.request.RegistrationRequest;
import kitchen.sink.demo.dto.response.RegistrationResponse;
import kitchen.sink.demo.entity.Member;
import kitchen.sink.demo.event.MemberRegistered;
import kitchen.sink.demo.repository.MemberRepository;
import kitchen.sink.demo.security.PasswordPolicy;
import kitchen.sink.demo.service.RegistrationService;
import kitchen.sink.demo.util.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationServiceImpl implements RegistrationService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final ApplicationEventPublisher events;
    private static final Logger log = LoggerFactory.getLogger(RegistrationServiceImpl.class);

    public RegistrationServiceImpl(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
                                   PasswordPolicy passwordPolicy, ApplicationEventPublisher events) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.events = events;
    }

    /** Validates the password, saves the member and announces it (published to Kafka after the commit). */
    @Override
    @Transactional
    public RegistrationResponse registerUser(RegistrationRequest request) {
        log.debug("registering a new member");
        String email = EmailNormalizer.normalize(request.email());
        passwordPolicy.validate(request.password());
        String passwordHash = passwordEncoder.encode(request.password());
        Member member = new Member(email, request.phoneNo(), request.firstName(), request.lastName(), passwordHash);
        // a duplicate email surfaces as DuplicateKeyException (unique index), which becomes the 409
        memberRepository.save(member);
        events.publishEvent(new MemberRegistered(member.getId(), member.getEmail(), member.getPhoneNo(),
                member.getFirstName(), member.getLastName()));
        log.info("member {} registered", member.getId());
        return new RegistrationResponse("User Registered");
    }
}
