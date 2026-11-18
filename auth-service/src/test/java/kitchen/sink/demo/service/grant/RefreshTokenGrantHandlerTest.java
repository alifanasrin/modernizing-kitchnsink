package kitchen.sink.demo.service.grant;

import kitchen.sink.demo.dto.request.TokenRequest;
import kitchen.sink.demo.entity.Member;
import kitchen.sink.demo.entity.OAuthClient;
import kitchen.sink.demo.entity.Session;
import kitchen.sink.demo.exception.InvalidGrantException;
import kitchen.sink.demo.repository.MemberRepository;
import kitchen.sink.demo.repository.OAuthClientRepository;
import kitchen.sink.demo.security.AccessTokenIssuer;
import kitchen.sink.demo.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenGrantHandlerTest {

    static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock SessionService sessions;
    @Mock MemberRepository members;
    @Mock OAuthClientRepository clients;
    @Mock AccessTokenIssuer accessTokens;

    RefreshTokenGrantHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RefreshTokenGrantHandler(sessions, members, clients, accessTokens,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static TokenRequest request(String refreshToken) {
        return new TokenRequest("refresh_token", "spa", null, null, refreshToken);
    }

    private void givenClient() {
        when(clients.findByClientId("spa")).thenReturn(Optional.of(new OAuthClient("spa", "Web App", java.util.List.of("api"), Set.of("u"))));
    }

    private static Session session() {
        Session s = new Session("ABCD2345", "spa", "newhash", NOW, NOW.plus(Duration.ofDays(7)), "1.2.3.4");
        org.springframework.test.util.ReflectionTestUtils.setField(s, "id", "sess1");
        return s;
    }

    private static Member member() {
        Member m = new Member("ann@example.com", "1234567890", "Ann", "Lee", "$2a$hash");
        org.springframework.test.util.ReflectionTestUtils.setField(m, "id", "ABCD2345");
        return m;
    }

    @Test
    void grantType_isRefreshToken() {
        assertThat(handler.grantType()).isEqualTo("refresh_token");
    }


    @Test
    @Tag("critical")
    void handle_returnsTheNewTokens() {
        givenClient();
        when(sessions.rotateRefreshToken(any(), any(), any())).thenReturn(session());
        when(members.findById("ABCD2345")).thenReturn(Optional.of(member()));
        when(accessTokens.issueAccessToken(any(), any(), any(), any(), any(), any(), any())).thenReturn("jwt");

        assertThat(handler.handle(request("r1"), "ip").accessToken()).isEqualTo("jwt");
    }

    @Test
    @Tag("critical")
    void handle_turnsARefusedTokenIntoInvalidGrant() {
        givenClient();
        when(sessions.rotateRefreshToken(any(), any(), any()))
                .thenThrow(new InvalidGrantException("Invalid or expired refresh token"));

        assertThatThrownBy(() -> handler.handle(request("bad"), "ip")).isInstanceOf(InvalidGrantException.class);
    }

    @Test
    void validateMandatoryParams_refusesAMissingRefreshToken() {
        assertThatThrownBy(() -> handler.validateMandatoryParams(request(null))).isInstanceOf(InvalidGrantException.class);
    }
}
