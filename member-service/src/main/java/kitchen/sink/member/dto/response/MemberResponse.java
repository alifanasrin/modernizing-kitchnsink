package kitchen.sink.member.dto.response;

import kitchen.sink.member.entity.Member;

public record MemberResponse(String firstName, String lastName, String phoneNo, String email) {

    /** Builds the response from a member; only these four fields ever leave the service. */
    public static MemberResponse from(Member member) {
        return new MemberResponse(member.firstName(), member.lastName(), member.phoneNo(), member.email());
    }
}
