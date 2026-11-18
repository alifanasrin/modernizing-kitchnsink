package kitchen.sink.demo.service.grant;

import kitchen.sink.demo.dto.request.TokenRequest;
import kitchen.sink.demo.dto.response.TokenResponse;
import kitchen.sink.demo.entity.AuthorizationGrant;
import kitchen.sink.demo.entity.Member;
import kitchen.sink.demo.entity.OAuthClient;
import kitchen.sink.demo.exception.InvalidGrantException;
import kitchen.sink.demo.repository.AuthorizationGrantStore;
import kitchen.sink.demo.repository.MemberRepository;
import kitchen.sink.demo.repository.OAuthClientRepository;
import kitchen.sink.demo.security.AccessTokenIssuer;
import kitchen.sink.demo.service.SessionService;
import kitchen.sink.demo.util.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationCodeGrantHandlerTest {

    static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    // the example from RFC 7636 appendix B
    static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    static final String URI = "https://app.example.com/cb";

    @Mock AuthorizationGrantStore grants;
    @Mock OAuthClientRepository clients;
    @Mock MemberRepository members;
    @Mock SessionService sessions;
    @Mock AccessTokenIssuer accessTokens;
    AuthorizationCodeGrantHandler service;

    @BeforeEach
    void setUp() {
        service = new AuthorizationCodeGrantHandler(grants, clients, members, sessions, accessTokens,
                new kitchen.sink.demo.config.JwtProperties(null, null, "iss", null, java.time.Duration.ofMinutes(15), java.time.Duration.ofDays(7)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static TokenRequest request(String verifier) {
        return new TokenRequest("authorization_code", "spa", "c0de", verifier, null);
    }

    private static AuthorizationGrant grant(java.time.Instant expiresAt) {
        AuthorizationGrant g = new AuthorizationGrant(null, "spa", URI, CHALLENGE, NOW, expiresAt);
        org.springframework.test.util.ReflectionTestUtils.setField(g, "userId", "ABCD2345");
        return g;
    }

    private void givenIssuedCode() {
        when(grants.consumeCode(TokenHasher.hash("c0de"), NOW)).thenReturn(Optional.of(
                grant(NOW.plusSeconds(60))));
    }

    @Test
    @Tag("critical")
    void handle_startsASession_whenCodeAndVerifierMatch() {
        givenIssuedCode();
        when(clients.findByClientId("spa")).thenReturn(Optional.of(new OAuthClient("spa", "Web", java.util.List.of("api", "billing"), Set.of(URI))));
        Member member = new Member("ann@example.com", "1234567890", "Ann", "Lee", "$2a$h");
        org.springframework.test.util.ReflectionTestUtils.setField(member, "id", "ABCD2345");
        when(members.findById("ABCD2345")).thenReturn(Optional.of(member));
        when(accessTokens.issueAccessToken(eq("ann@example.com"), anyString(), eq(java.util.List.of("api", "billing")), eq("spa"), eq("1.2.3.4"), eq(NOW),
                eq(NOW.plus(java.time.Duration.ofDays(7))))).thenReturn("jwt");
        when(accessTokens.accessTokenTtlSeconds(NOW, NOW.plus(java.time.Duration.ofDays(7)))).thenReturn(900L);

        TokenResponse tokens = service.handle(request(VERIFIER), "1.2.3.4");

        String sessionId = tokens.sessionId();
        assertThat(tokens.accessToken()).isEqualTo("jwt");
        assertThat(tokens.tokenType()).isEqualTo("Bearer");
        assertThat(tokens.expiresIn()).isEqualTo(900);
        assertThat(tokens.refreshToken()).doesNotContain(".").doesNotContain(sessionId);
        // the session is saved after the tokens exist, with only the hash of the refresh secret
        String secret = tokens.refreshToken();
        InOrder order = inOrder(accessTokens, sessions);
        order.verify(accessTokens).issueAccessToken(eq("ann@example.com"), eq(sessionId), eq(java.util.List.of("api", "billing")), eq("spa"), eq("1.2.3.4"), eq(NOW),
                eq(NOW.plus(java.time.Duration.ofDays(7))));
        order.verify(sessions).start(sessionId, "ABCD2345", "spa", TokenHasher.hash(secret), "1.2.3.4",
                NOW.plus(java.time.Duration.ofDays(7)));
        // the session is remembered on the grant, so a replay of the code can revoke it
        verify(grants).attachSession(null, sessionId);
    }

    @Test
    @Tag("critical")
    void handle_revokesTheSessionThatTheCodeAlreadyProduced_whenTheCodeIsReplayed() {
        when(grants.consumeCode(any(), any())).thenReturn(Optional.empty());
        AuthorizationGrant used = grant(NOW);
        org.springframework.test.util.ReflectionTestUtils.setField(used, "sessionId", "sess1");
        when(grants.findUsed(TokenHasher.hash("c0de"))).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service.handle(request(VERIFIER), "ip")).isInstanceOf(InvalidGrantException.class);

        verify(sessions).revoke("sess1");
        verify(sessions, never()).start(any(), any(), any(), any(), any(), any());
    }

    @Test
    @Tag("critical")
    void handle_revokesNothing_whenTheCodeIsUnknownOrNeverProducedASession() {
        when(grants.consumeCode(any(), any())).thenReturn(Optional.empty());
        when(grants.findUsed(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(request(VERIFIER), "ip")).isInstanceOf(InvalidGrantException.class);

        AuthorizationGrant usedWithoutSession = grant(NOW);
        when(grants.findUsed(any())).thenReturn(Optional.of(usedWithoutSession));
        assertThatThrownBy(() -> service.handle(request(VERIFIER), "ip")).isInstanceOf(InvalidGrantException.class);

        verify(sessions, never()).revoke(any());
    }

    @Test
    @Tag("critical")
    void handle_refusesAWrongVerifier_withoutStartingASession() {
        givenIssuedCode();

        assertThatThrownBy(() -> service.handle(request(VERIFIER.replace('d', 'e')), "ip"))
                .isInstanceOf(InvalidGrantException.class);

        verify(sessions, never()).start(any(), any(), any(), any(), any(), any());
    }

    @Test
    @Tag("critical")
    void handle_refusesAnUnknownOrUsedCode() {
        when(grants.consumeCode(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(request(VERIFIER), "ip")).isInstanceOf(InvalidGrantException.class);
    }

    @Test
    @Tag("critical")
    void handle_refusesAnotherClient() {
        givenIssuedCode();
        assertThatThrownBy(() -> service.handle(
                new TokenRequest("authorization_code", "other", "c0de", VERIFIER, null), "ip"))
                .isInstanceOf(InvalidGrantException.class);
    }

    @Test
    void validateMandatoryParams_refusesAMissingCodeOrVerifier() {
        for (TokenRequest bad : new TokenRequest[] {
                new TokenRequest("authorization_code", "spa", null, VERIFIER, null),
                new TokenRequest("authorization_code", "spa", "c0de", " ", null)}) {
            assertThatThrownBy(() -> service.validateMandatoryParams(bad))
                    .isInstanceOf(kitchen.sink.demo.exception.InvalidGrantException.class);
        }
        service.validateMandatoryParams(request(VERIFIER));
    }

    @Test
    void handle_refusesWhenTheClientWasDisabledMeanwhile() {
        givenIssuedCode();
        when(clients.findByClientId("spa")).thenReturn(Optional.empty());
        when(members.findById("ABCD2345")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(request(VERIFIER), "ip")).isInstanceOf(InvalidGrantException.class);

        verify(sessions, never()).start(any(), any(), any(), any(), any(), any());
    }

}
