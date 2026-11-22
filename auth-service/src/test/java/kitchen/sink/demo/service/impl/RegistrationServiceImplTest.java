package kitchen.sink.demo.service.impl;

import feign.FeignException;
import feign.Request;
import kitchen.sink.demo.client.MemberServiceClient;
import kitchen.sink.demo.exception.MemberServiceException;
import kitchen.sink.demo.dto.request.CreateMemberRequest;
import kitchen.sink.demo.dto.request.RegistrationRequest;
import kitchen.sink.demo.dto.response.RegistrationResponse;
import kitchen.sink.demo.entity.Member;
import kitchen.sink.demo.repository.MemberRepository;
import kitchen.sink.demo.security.PasswordPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;

import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

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
    MemberServiceClient memberServiceClient;

    RegistrationServiceImpl service;

    static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    @BeforeEach
    void setUp() {
        service = new RegistrationServiceImpl(memberRepository, passwordEncoder, passwordPolicy, memberServiceClient);
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
        verify(memberServiceClient, never()).createMember(any());
    }

    @Test
    @Tag("critical")
    void shouldCreateMemberInMemberService_withoutAnyPassword() {
        service.registerUser(validRequest());

        verify(memberServiceClient).createMember(
                new CreateMemberRequest("ann@example.com", "1234567890", "Ann", "Lee"));
    }

    private static FeignException feignError(int status) {
        var request = Request.create(Request.HttpMethod.POST, "http://member-service/members", Map.of(),
                new byte[0], StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("createMember",
                feign.Response.builder().status(status).request(request).headers(Map.of()).build());
    }

    @Test
    @Tag("critical")
    void shouldUndoRegistrationAnd503_whenMemberServiceIsUnavailable() {
        doThrow(feignError(503)).when(memberServiceClient).createMember(any());

        assertThatThrownBy(() -> service.registerUser(validRequest()))
                .isInstanceOfSatisfying(MemberServiceException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(memberRepository).delete(any(Member.class));
    }

    @Test
    @Tag("critical")
    void shouldUndoRegistrationAnd500_whenMemberServiceFails() {
        doThrow(feignError(500)).when(memberServiceClient).createMember(any());

        assertThatThrownBy(() -> service.registerUser(validRequest()))
                .isInstanceOfSatisfying(MemberServiceException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
        verify(memberRepository).delete(any(Member.class));
    }

    @Test
    void shouldAnswer409_whenMemberServiceAlreadyHasTheEmail() {
        doThrow(feignError(409)).when(memberServiceClient).createMember(any());

        assertThatThrownBy(() -> service.registerUser(validRequest()))
                .isInstanceOfSatisfying(MemberServiceException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(memberRepository).delete(any(Member.class));
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
