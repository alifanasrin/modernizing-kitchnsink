package kitchen.sink.demo.service.impl;

import kitchen.sink.demo.client.MemberServiceClient;
import kitchen.sink.demo.dto.request.CreateMemberRequest;
import kitchen.sink.demo.dto.request.RegistrationRequest;
import kitchen.sink.demo.dto.response.RegistrationResponse;
import feign.FeignException;
import kitchen.sink.demo.entity.Member;
import kitchen.sink.demo.exception.MemberServiceException;
import kitchen.sink.demo.repository.MemberRepository;
import kitchen.sink.demo.security.PasswordPolicy;
import kitchen.sink.demo.service.RegistrationService;
import kitchen.sink.demo.util.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationServiceImpl implements RegistrationService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final MemberServiceClient memberServiceClient;
    private static final Logger log = LoggerFactory.getLogger(RegistrationServiceImpl.class);

    public RegistrationServiceImpl(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
                                   PasswordPolicy passwordPolicy, MemberServiceClient memberServiceClient) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.memberServiceClient = memberServiceClient;
    }

    /** Validates the password, saves the member and creates it in member-service (undone if that fails). */
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
        createInMemberService(member);
        log.info("member {} registered", member.getId());
        return new RegistrationResponse("User Registered");
    }

    /**
     * Creates the member in member-service. If that fails the registration is undone: the saved member is deleted
     * (a MongoDB transaction would also roll back, but a standalone local MongoDB has none) and a
     * {@link MemberServiceException} carries the status to answer with. A 409 (member-service already has this
     * email) is answered like a duplicate registration.
     */
    private void createInMemberService(Member member) {
        try {
            memberServiceClient.createMember(new CreateMemberRequest(member.getEmail(), member.getPhoneNo(),
                    member.getFirstName(), member.getLastName()));
            log.info("member {} created in member-service", member.getId());
        } catch (FeignException e) {
            undoRegistration(member);
            // status -1 = no answer at all (connection refused, timeout)
            HttpStatus status = e.status() == 409 ? HttpStatus.CONFLICT
                    : (e.status() == 503 || e.status() == -1) ? HttpStatus.SERVICE_UNAVAILABLE
                    : HttpStatus.INTERNAL_SERVER_ERROR;
            log.error("member {} was not created in member-service (status {})", member.getId(), e.status());
            throw new MemberServiceException(status, switch (status) {
                case CONFLICT -> "Member Already Exist";
                case SERVICE_UNAVAILABLE -> "Registration is temporarily unavailable, please try again later";
                default -> "Something went wrong, please try again later";
            }, e);
        }
    }

    private void undoRegistration(Member member) {
        try {
            memberRepository.delete(member);
        } catch (RuntimeException e) {
            log.error("member {} could not be deleted after the member-service call failed", member.getId(), e);
        }
    }
}
