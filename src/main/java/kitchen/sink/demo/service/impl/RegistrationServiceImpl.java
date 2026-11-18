package kitchen.sink.demo.service.impl;

import kitchen.sink.demo.dto.request.RegistrationRequest;
import kitchen.sink.demo.dto.response.RegistrationResponse;
import kitchen.sink.demo.entity.Member;
import kitchen.sink.demo.repository.MemberRepository;
import kitchen.sink.demo.security.PasswordPolicy;
import kitchen.sink.demo.service.RegistrationService;
import kitchen.sink.demo.util.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationServiceImpl implements RegistrationService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private static final Logger log = LoggerFactory.getLogger(RegistrationServiceImpl.class);

    public RegistrationServiceImpl(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
                                   PasswordPolicy passwordPolicy) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }



    /** Validates the password and saves the member. */
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
        log.info("member {} registered", member.getId());
        return new RegistrationResponse("User Registered");
    }
}
