package kitchen.sink.member.service;

import kitchen.sink.member.dto.request.CreateMemberRequest;
import kitchen.sink.member.dto.request.MemberRequest;
import kitchen.sink.member.dto.response.MemberResponse;
import kitchen.sink.member.dto.response.PaginatedResponse;

public interface MemberService {

    /** Returns a page of members sorted by name. */
    PaginatedResponse<MemberResponse> getMembers(MemberRequest memberRequest);

    /** Returns the member with the given email address. */
    MemberResponse findByEmail(String email);

    /** Creates the member; throws {@code MemberAlreadyExistsException} if the email is taken. */
    MemberResponse createMember(CreateMemberRequest request);
}
