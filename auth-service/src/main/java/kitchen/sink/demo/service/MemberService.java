package kitchen.sink.demo.service;

import kitchen.sink.demo.dto.request.MemberRequest;
import kitchen.sink.demo.dto.response.MemberResponse;
import kitchen.sink.demo.dto.response.PaginatedResponse;

public interface MemberService {

    /** Returns a page of members sorted by name. */
    PaginatedResponse<MemberResponse> getMembers(MemberRequest memberRequest);

    /** Returns the member with the given email address. */
    MemberResponse findByEmail(String email);
}
