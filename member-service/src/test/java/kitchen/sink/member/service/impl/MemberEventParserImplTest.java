package kitchen.sink.member.service.impl;

import jakarta.validation.Validation;
import kitchen.sink.member.event.MemberRegisteredEvent;
import kitchen.sink.member.exception.InvalidMemberEventException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberEventParserImplTest {

    final MemberEventParserImpl parser = new MemberEventParserImpl(JsonMapper.builder().build(),
            Validation.buildDefaultValidatorFactory().getValidator());

    static String payload(String eventType, String version, String email, String phone) {
        return """
                {"eventId":"e1","eventType":"%s","schemaVersion":"%s","source":"auth-service",
                 "occurredAt":"2026-11-19T10:00:00Z",
                 "data":{"memberId":"64f0","email":"%s","phoneNo":"%s","firstName":"Ann","lastName":"Lee"}}
                """.formatted(eventType, version, email, phone);
    }

    @Test
    @Tag("critical")
    void shouldParseValidEvent() {
        MemberRegisteredEvent event = parser.parse(payload("MEMBER_REGISTERED", "1.0", "ann@example.com", "1234567890"));

        assertThat(event.data().email()).isEqualTo("ann@example.com");
        assertThat(event.data().firstName()).isEqualTo("Ann");
    }

    @Test
    void shouldAcceptNewerMinorVersionAndUnknownFields() {
        String json = payload("MEMBER_REGISTERED", "1.4", "ann@example.com", "1234567890")
                .replace("\"source\"", "\"extra\":true,\"source\"");

        assertThat(parser.parse(json).schemaVersion()).isEqualTo("1.4");
    }

    @Test
    @Tag("critical")
    void shouldRejectMalformedJson() {
        assertThatThrownBy(() -> parser.parse("not json")).isInstanceOf(InvalidMemberEventException.class);
    }

    @Test
    void shouldRejectUnsupportedTypeAndMajorVersion() {
        assertThatThrownBy(() -> parser.parse(payload("OTHER", "1.0", "ann@example.com", "1234567890")))
                .isInstanceOf(InvalidMemberEventException.class).hasMessageContaining("eventType");
        assertThatThrownBy(() -> parser.parse(payload("MEMBER_REGISTERED", "2.0", "ann@example.com", "1234567890")))
                .isInstanceOf(InvalidMemberEventException.class).hasMessageContaining("schemaVersion");
    }

    @Test
    @Tag("critical")
    void shouldRejectInvalidData_withoutLeakingValues() {
        assertThatThrownBy(() -> parser.parse(payload("MEMBER_REGISTERED", "1.0", "not-an-email", "12ab")))
                .isInstanceOf(InvalidMemberEventException.class)
                .hasMessageContaining("data.email")
                .hasMessageContaining("data.phoneNo")
                .hasMessageNotContaining("not-an-email");
    }
}
