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

    static String payload(String email, String phone) {
        return """
                {"data":{"memberId":"64f0","email":"%s","phoneNo":"%s","firstName":"Ann","lastName":"Lee"}}
                """.formatted(email, phone);
    }

    @Test
    @Tag("critical")
    void shouldParseValidEvent() {
        MemberRegisteredEvent event = parser.parse(payload("ann@example.com", "1234567890"));

        assertThat(event.data().email()).isEqualTo("ann@example.com");
        assertThat(event.data().firstName()).isEqualTo("Ann");
    }

    @Test
    void shouldIgnoreUnknownFields() {
        String json = payload("ann@example.com", "1234567890")
                .replace("\"data\"", "\"extra\":true,\"source\":\"x\",\"data\"");

        assertThat(parser.parse(json).data().email()).isEqualTo("ann@example.com");
    }

    @Test
    @Tag("critical")
    void shouldRejectMalformedJson() {
        assertThatThrownBy(() -> parser.parse("not json")).isInstanceOf(InvalidMemberEventException.class);
    }

    @Test
    @Tag("critical")
    void shouldRejectInvalidData_withoutLeakingValues() {
        assertThatThrownBy(() -> parser.parse(payload("not-an-email", "12ab")))
                .isInstanceOf(InvalidMemberEventException.class)
                .hasMessageContaining("data.email")
                .hasMessageContaining("data.phoneNo")
                .hasMessageNotContaining("not-an-email");
    }
}
