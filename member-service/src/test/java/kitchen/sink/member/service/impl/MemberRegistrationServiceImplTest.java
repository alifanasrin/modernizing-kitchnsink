package kitchen.sink.member.service.impl;

import kitchen.sink.member.entity.Member;
import kitchen.sink.member.event.MemberRegisteredEvent;
import kitchen.sink.member.exception.InvalidMemberEventException;
import kitchen.sink.member.service.MemberEventParser;
import kitchen.sink.member.service.MemberService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberRegistrationServiceImplTest {

    @Mock MemberEventParser parser;
    @Mock MemberService memberService;
    @InjectMocks MemberRegistrationServiceImpl service;

    static MemberRegisteredEvent event() {
        return new MemberRegisteredEvent(
                new MemberRegisteredEvent.Data("64f0", " Ann@Example.com ", "1234567890", "Ann", "Lee"));
    }

    @Test
    @Tag("critical")
    void shouldCreateMemberWithNormalizedEmail() {
        when(parser.parse("payload")).thenReturn(event());
        when(memberService.createMember(any())).thenReturn(true);

        service.process("payload");

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberService).createMember(saved.capture());
        assertThat(saved.getValue()).isEqualTo(new Member(null, "ann@example.com", "1234567890", "Ann", "Lee"));
    }

    @Test
    void shouldNotFail_whenMemberAlreadyExists() {
        when(parser.parse("payload")).thenReturn(event());
        when(memberService.createMember(any())).thenReturn(false);

        service.process("payload");

        verify(memberService).createMember(any());
    }

    @Test
    @Tag("critical")
    void shouldNotCreateAMember_whenPayloadIsInvalid() {
        when(parser.parse("bad")).thenThrow(new InvalidMemberEventException("Malformed event payload"));

        assertThatThrownBy(() -> service.process("bad")).isInstanceOf(InvalidMemberEventException.class);
        verify(memberService, never()).createMember(any());
    }
}
