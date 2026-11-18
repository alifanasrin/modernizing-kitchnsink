package kitchen.sink.demo.dto.response;

import kitchen.sink.demo.entity.Member;

public record MemberResponse(String firstName, String lastName, String phoneNo, String email) {

    /** Builds the response from a member; only these four fields ever leave the service. */
    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getFirstName(), member.getLastName(), member.getPhoneNo(), member.getEmail());
    }
}
