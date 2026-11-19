package kitchen.sink.demo.service.impl;

import kitchen.sink.demo.dto.request.RegistrationRequest;
import kitchen.sink.demo.dto.response.RegistrationResponse;
import kitchen.sink.demo.entity.Member;
import kitchen.sink.demo.event.MemberRegistered;
import kitchen.sink.demo.repository.MemberRepository;
import kitchen.sink.demo.security.PasswordPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    @Mock
    MemberRepository memberRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    PasswordPolicy passwordPolicy;

    @Mock
    ApplicationEventPublisher events;

    RegistrationServiceImpl service;

    static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    @BeforeEach
    void setUp() {
        service = new RegistrationServiceImpl(memberRepository, passwordEncoder, passwordPolicy, events);
    }

    private static RegistrationRequest validRequest() {
        return new RegistrationRequest("Ann", "Lee", "ann@example.com", "1234567890", "s3cretPass");
    }

    @Test
    @Tag("critical")
    void shouldSaveMemberWithRequestFields_whenRequestIsValid() {
        service.registerUser(validRequest());

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(saved.getValue().getFirstName()).isEqualTo("Ann");
        assertThat(saved.getValue().getLastName()).isEqualTo("Lee");
        assertThat(saved.getValue().getEmail()).isEqualTo("ann@example.com");
        assertThat(saved.getValue().getPhoneNo()).isEqualTo("1234567890");
    }

    @Test
    @Tag("critical")
    void shouldStoreHashedPassword_neverTheRawOne() {
        when(passwordEncoder.encode("s3cretPass")).thenReturn("$2a$hashed");

        service.registerUser(validRequest());

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$hashed");
    }

    @Test
    @Tag("critical")
    void shouldNotRetry_whenEmailIsDuplicate() {
        when(memberRepository.save(any(Member.class)))
                .thenThrow(new DuplicateKeyException("E11000 index: uk_member_email dup key"));
        RegistrationRequest request = validRequest();

        assertThatThrownBy(() -> service.registerUser(request)).isInstanceOf(DuplicateKeyException.class);
        verify(memberRepository, times(1)).save(any(Member.class));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @Tag("critical")
    void shouldAnnounceRegisteredMember_withoutAnyPassword() {
        service.registerUser(validRequest());

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new MemberRegistered(null, "ann@example.com", "1234567890", "Ann", "Lee"));
    }

    @Test
    void shouldSaveLowerCasedTrimmedEmail_whenEmailHasMixedCase() {
        service.registerUser(new RegistrationRequest("Ann", "Lee", "  Ann@Example.COM ", "1234567890", "s3cretPass"));

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ann@example.com");
    }

    @Test
    void shouldReturnSuccessMessage_whenRequestIsValid() {
        RegistrationResponse response = service.registerUser(validRequest());

        assertThat(response.message()).isEqualTo("User Registered");
    }

    @Test
    @Tag("critical")
    void shouldPropagateDuplicateKeyException_whenEmailAlreadyExists() {
        when(memberRepository.save(any(Member.class))).thenThrow(new DuplicateKeyException("duplicate"));
        RegistrationRequest request = validRequest();

        assertThatThrownBy(() -> service.registerUser(request))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @Tag("critical")
    void shouldSaveTheMemberWithTheEncodedPasswordOnly() {
        service.registerUser(validRequest());

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo(validRequest().password());
    }

}
