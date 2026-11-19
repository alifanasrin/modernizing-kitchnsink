package kitchen.sink.member.service.impl;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import kitchen.sink.member.event.MemberRegisteredEvent;
import kitchen.sink.member.exception.InvalidMemberEventException;
import kitchen.sink.member.service.MemberEventParser;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MemberEventParserImpl implements MemberEventParser {

    private final JsonMapper jsonMapper;
    private final Validator validator;

    public MemberEventParserImpl(JsonMapper jsonMapper, Validator validator) {
        this.jsonMapper = jsonMapper;
        this.validator = validator;
    }

    @Override
    public MemberRegisteredEvent parse(String payload) {
        MemberRegisteredEvent event;
        try {
            event = jsonMapper.readValue(payload, MemberRegisteredEvent.class);
        } catch (RuntimeException e) {
            throw new InvalidMemberEventException("Malformed event payload", e);
        }
        if (event == null) {
            throw new InvalidMemberEventException("Empty event payload");
        }
        Set<ConstraintViolation<MemberRegisteredEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) { // property paths and messages only, never the values
            throw new InvalidMemberEventException("Invalid event: " + violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().collect(Collectors.joining(", ")));
        }
        if (!MemberRegisteredEvent.EVENT_TYPE.equals(event.eventType())) {
            throw new InvalidMemberEventException("Unsupported eventType: " + event.eventType());
        }
        if (!event.schemaVersion().startsWith(MemberRegisteredEvent.SUPPORTED_MAJOR_VERSION + ".")) {
            throw new InvalidMemberEventException("Unsupported schemaVersion: " + event.schemaVersion());
        }
        return event;
    }
}
