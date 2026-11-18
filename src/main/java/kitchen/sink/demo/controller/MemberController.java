package kitchen.sink.demo.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kitchen.sink.demo.dto.request.MemberRequest;
import kitchen.sink.demo.dto.response.MemberResponse;
import kitchen.sink.demo.dto.response.PaginatedResponse;
import kitchen.sink.demo.service.MemberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
