package kitchen.sink.member.service;

public interface MemberRegistrationService {

    /** Creates the member of a member-registered payload; a member that already exists is left untouched. */
    void process(String payload);
}
