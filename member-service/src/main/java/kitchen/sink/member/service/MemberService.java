package kitchen.sink.member.service;

import kitchen.sink.member.dto.request.MemberRequest;
import kitchen.sink.member.dto.response.MemberResponse;
import kitchen.sink.member.dto.response.PaginatedResponse;
import kitchen.sink.member.entity.Member;

public interface MemberService {

    /** Returns a page of members sorted by name. */
    PaginatedResponse<MemberResponse> getMembers(MemberRequest memberRequest);

    /** Returns the member with the given email address. */
    MemberResponse findByEmail(String email);


    boolean createMember(Member member);
}
