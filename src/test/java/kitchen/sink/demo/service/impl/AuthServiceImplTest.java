package kitchen.sink.demo.service.impl;

import kitchen.sink.demo.constant.Constant;
import kitchen.sink.demo.dto.response.AuthorizationCodeResponse;
import kitchen.sink.demo.dto.request.LoginRequest;
import kitchen.sink.demo.entity.Member;
import kitchen.sink.demo.exception.AuthenticationFailedException;
import kitchen.sink.demo.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import kitchen.sink.demo.security.MemberPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import kitchen.sink.demo.service.SessionService;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock MemberRepository memberRepository;
    @Mock AuthenticationManager authenticationManager;
    @Mock SessionService sessionService;
    @Mock kitchen.sink.demo.service.AuthorizationCodeService authorizationCodes;
    @Mock JwtDecoder jwtDecoder;

    AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        kitchen.sink.demo.security.PasswordPolicy policy = new kitchen.sink.demo.security.PasswordPolicy();
        service = new AuthServiceImpl(authenticationManager, policy, sessionService, authorizationCodes, jwtDecoder);
    }


    /** Makes the authentication manager accept the credentials and return this member, like the real provider. */
    private void givenPasswordAccepted(String email, String password, Member m) {
        when(authenticationManager.authenticate(any())).thenAnswer(inv -> {
            UsernamePasswordAuthenticationToken t = inv.getArgument(0);
            assertThat(t.getName()).isEqualTo(email);
            assertThat(t.getCredentials()).isEqualTo(password);
            return UsernamePasswordAuthenticationToken.authenticated(new MemberPrincipal(m), null, java.util.List.of());
        });
    }

    private void givenPasswordRefused() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
    }

    static LoginRequest login(String email, String password) {
        return new LoginRequest(email, password, "req-1");
    }

    private static Member member() {
        Member m = new Member("ann@example.com", "1234567890", "Ann", "Lee", "$2a$hash");
        org.springframework.test.util.ReflectionTestUtils.setField(m, "id", "ABCD2345");
        return m;
    }

    @Test
    @Tag("critical")
    void login_issuesAuthorizationCodeAndNoSession_whenCredentialsMatch() {
        Member m = member();
        givenPasswordAccepted("ann@example.com", "s3cretPass", m);
        AuthorizationCodeResponse code = new AuthorizationCodeResponse("https://app.example.com/cb?code=c0de", 60);
        when(authorizationCodes.complete("req-1", "ABCD2345")).thenReturn(code);

        assertThat(service.login(login("ann@example.com", "s3cretPass"), "ip")).isSameAs(code);

        verify(authorizationCodes).validate("req-1");
        verify(sessionService, never()).start(any(), any(), any(), any(), any(), any());
    }

    @Test
    @Tag("critical")
    void login_issuesNoCode_whenPasswordWrong() {
        givenPasswordRefused();

        assertThatThrownBy(() -> service.login(login("ann@example.com", "wrong"), "ip"))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(authorizationCodes, never()).complete(any(), any());
    }

    // ---- login ----

    @Test
    @Tag("critical")
    void login_rejectsWrongPassword() {
        givenPasswordRefused();

        assertThatThrownBy(() -> service.login(login("ann@example.com", "wrong"), "ip"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage(Constant.BAD_CREDENTIALS);
        verify(sessionService, never()).start(any(), any(), any(), any(), any(), any());
    }

    @Test
    @Tag("critical")
    void login_rejectsUnknownEmailWithSameMessage() {
        givenPasswordRefused();

        assertThatThrownBy(() -> service.login(login("nobody@example.com", "whatever1"), "ip"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage(Constant.BAD_CREDENTIALS);
        verify(authorizationCodes, never()).complete(any(), any());
    }

    @Test
    @Tag("critical")
    void login_rejectsAnOverLongPassword_withoutCallingTheAuthenticationManager() {
        String tooLong = "a".repeat(2000);

        assertThatThrownBy(() -> service.login(login("ann@example.com", tooLong), "ip"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage(Constant.BAD_CREDENTIALS);
        verify(authenticationManager, never()).authenticate(any());
    }

    // ---- refresh ----

    private static Jwt token(String subject, String sessionId) {
        Jwt.Builder b = Jwt.withTokenValue("tok").header("alg", "RS256").subject(subject)
                .issuedAt(Instant.parse("2026-01-01T10:00:00Z")).expiresAt(Instant.parse("2026-01-01T10:15:00Z"));
        if (sessionId != null) {
            b.claim("sid", sessionId);
        }
        return b.build();
    }

    @Test
    @Tag("critical")
    void logout_revokesTheSessionNamedInTheAccessToken() {
        when(jwtDecoder.decode("tok")).thenReturn(token("ann@example.com", "sess1"));

        service.logout("tok");

        verify(sessionService).revoke("sess1");
    }

    @Test
    @Tag("critical")
    void logout_rejectsAnInvalidOrIncompleteAccessToken_andRevokesNothing() {
        when(jwtDecoder.decode("bad")).thenThrow(new BadJwtException("nope"));
        when(jwtDecoder.decode("nosid")).thenReturn(token("ann@example.com", null));

        assertThatThrownBy(() -> service.logout("bad")).isInstanceOf(AuthenticationFailedException.class)
                .hasMessage(Constant.BAD_ACCESS_TOKEN);
        assertThatThrownBy(() -> service.logout("nosid")).isInstanceOf(AuthenticationFailedException.class);

        verify(sessionService, never()).revoke(any());
    }

}
