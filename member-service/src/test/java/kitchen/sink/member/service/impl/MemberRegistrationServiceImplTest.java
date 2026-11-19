package kitchen.sink.member.service.impl;

import kitchen.sink.member.entity.Member;
import kitchen.sink.member.event.MemberRegisteredEvent;
import kitchen.sink.member.exception.InvalidMemberEventException;
import kitchen.sink.member.repository.MemberStore;
import kitchen.sink.member.service.MemberEventParser;
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
    @Mock MemberStore store;
    @InjectMocks MemberRegistrationServiceImpl service;

    static MemberRegisteredEvent event() {
        return new MemberRegisteredEvent("e1", "MEMBER_REGISTERED", "1.0", "auth-service", null,
                new MemberRegisteredEvent.Data("64f0", " Ann@Example.com ", "1234567890", "Ann", "Lee"));
    }

    @Test
    @Tag("critical")
    void shouldCreateMemberWithNormalizedEmail() {
        when(parser.parse("payload")).thenReturn(event());
        when(store.createIfAbsent(any())).thenReturn(true);

        service.process("payload");

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(store).createIfAbsent(saved.capture());
        assertThat(saved.getValue()).isEqualTo(new Member(null, "ann@example.com", "1234567890", "Ann", "Lee"));
    }

    @Test
    void shouldNotFail_whenMemberAlreadyExists() {
        when(parser.parse("payload")).thenReturn(event());
        when(store.createIfAbsent(any())).thenReturn(false);

        service.process("payload");

        verify(store).createIfAbsent(any());
    }

    @Test
    @Tag("critical")
    void shouldNotTouchTheStore_whenPayloadIsInvalid() {
        when(parser.parse("bad")).thenThrow(new InvalidMemberEventException("Malformed event payload"));

        assertThatThrownBy(() -> service.process("bad")).isInstanceOf(InvalidMemberEventException.class);
        verify(store, never()).createIfAbsent(any());
    }
}
