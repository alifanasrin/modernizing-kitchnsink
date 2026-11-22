package kitchen.sink.member.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kitchen.sink.member.dto.request.CreateMemberRequest;
import kitchen.sink.member.dto.request.MemberRequest;
import kitchen.sink.member.dto.response.MemberResponse;
import kitchen.sink.member.dto.response.PaginatedResponse;
import kitchen.sink.member.entity.Member;
import kitchen.sink.member.exception.MemberAlreadyExistsException;
import kitchen.sink.member.service.MemberService;
import kitchen.sink.member.util.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/members")
public class MemberController {

    private static final Logger log = LoggerFactory.getLogger(MemberController.class);

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    /** Returns a page of members sorted by name. */
    @GetMapping
    ResponseEntity<PaginatedResponse<MemberResponse>> members(
            @RequestParam(name = "pageNo", defaultValue = "1") @Min(1) Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") @Min(1) @Max(50) Integer pageSize) {
        log.debug("members page requested: pageNo={}, pageSize={}", pageNo, pageSize);
        return ResponseEntity.ok(memberService.getMembers(new MemberRequest(pageNo, pageSize)));
    }

    /** Returns the member with the given email address. */
    @GetMapping("/{email}")
    ResponseEntity<MemberResponse> findByEmail(@PathVariable(name = "email") @Email String email) {
        log.debug("member lookup requested");
        return ResponseEntity.ok(memberService.findByEmail(email));
    }

    /**
     * Creates the member that auth-service has just registered.
     *
     * <p>FUTURE SCOPE: restrict this API to service clients only. Introduce the concept of a service client
     * (a backend service with its own identity, as opposed to a user-facing app), issue it a token through the
     * OAuth 2 client-credentials grant, and require a dedicated permission (for example {@code members:create}) here.
     * Auth-service would be the only service client allowed to call it, so that no one can create a member without
     * going through registration.
     *
     * <p>UNTIL THEN: the endpoint is open in {@code SecurityConfig} because auth-service has no service-client token
     * yet. It must never be exposed publicly (no gateway or ingress route) and must only be reachable from the
     * internal network. Once service clients exist, remove the permitAll rule.
     */
    @PostMapping
    ResponseEntity<MemberResponse> createMember(@Valid @RequestBody CreateMemberRequest request) {
        log.debug("member creation requested");
        Member member = new Member(null, EmailNormalizer.normalize(request.email()), request.phoneNo(),
                request.firstName(), request.lastName());
        if (!memberService.createMember(member)) {
            throw new MemberAlreadyExistsException("Member already exists");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(MemberResponse.from(member));
    }
}
