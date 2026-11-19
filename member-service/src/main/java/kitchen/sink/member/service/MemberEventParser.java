package kitchen.sink.member.service;

import kitchen.sink.member.event.MemberRegisteredEvent;

public interface MemberEventParser {

    /** Parses and validates a raw payload, or throws {@code InvalidMemberEventException} (without event values). */
    MemberRegisteredEvent parse(String payload);
}
