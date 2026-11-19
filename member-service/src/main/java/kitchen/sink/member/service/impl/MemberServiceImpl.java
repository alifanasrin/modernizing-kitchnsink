package kitchen.sink.member.service.impl;

import org.springframework.dao.DuplicateKeyException;
import kitchen.sink.member.dto.request.MemberRequest;
import kitchen.sink.member.dto.response.MemberResponse;
import kitchen.sink.member.dto.response.PaginatedResponse;
import kitchen.sink.member.entity.Member;
import kitchen.sink.member.exception.MemberNotFoundException;
import kitchen.sink.member.repository.MemberRepository;
import kitchen.sink.member.service.MemberService;
import kitchen.sink.member.util.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemberServiceImpl implements MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberServiceImpl.class);

    private final MemberRepository memberRepository;

    public MemberServiceImpl(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /** Reads one page of members sorted by last and first name and maps it to a response. */
    @Override
    public PaginatedResponse<MemberResponse> getMembers(MemberRequest memberRequest) {
        log.info("listing members, pageNo={}, pageSize={}", memberRequest.pageNo(), memberRequest.pageSize());
        PageRequest pageRequest = PageRequest.of(memberRequest.pageNo() - 1, memberRequest.pageSize());
        Page<Member> memberPage = memberRepository.findAllByOrderByLastNameAscFirstNameAsc(pageRequest);
        List<MemberResponse> memberResponses = memberPage.getContent().stream().map(MemberResponse::from).toList();
        return new PaginatedResponse<>(memberResponses, memberPage.getSize(),
                memberPage.getNumber() + 1, memberPage.getTotalElements(), memberPage.getTotalPages());
    }

    /** Finds the member by email address or throws if none exists. */
    @Override
    public MemberResponse findByEmail(String email) {
        log.info("looking up a member by email");
        Member member = memberRepository.findByEmail(EmailNormalizer.normalize(email)).orElseThrow(() ->
                new MemberNotFoundException("Member Not Found with email " + email));
        return MemberResponse.from(member);
    }

    @Override
    public boolean createMember(Member member) {
       log.info("create a member");
       try {
           memberRepository.save(member);
           return true;
       }catch (DuplicateKeyException exception){
           log.debug("such a member already exist");
           return false;
       }
    }
}
