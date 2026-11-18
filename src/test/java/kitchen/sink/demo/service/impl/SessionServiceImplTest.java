package kitchen.sink.demo.service.impl;

import kitchen.sink.demo.entity.Session;
import kitchen.sink.demo.exception.AuthenticationFailedException;
import kitchen.sink.demo.exception.InvalidGrantException;
import kitchen.sink.demo.repository.SessionRepository;
import kitchen.sink.demo.util.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceImplTest {

    static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock SessionRepository sessionRepository;

    SessionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SessionServiceImpl(sessionRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Session persisted(String id, String hash) {
        Session s = new Session("ABCD2345", "spa", hash, NOW, NOW.plus(Duration.ofDays(7)), "1.2.3.4");
        org.springframework.test.util.ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    @Test
    @Tag("critical")
    void start_savesTheSessionWithTheGivenIdAndOnlyTheHashOfTheRefreshSecret() {
        service.start("sess1", "ABCD2345", "spa", TokenHasher.hash("secret"), "9.9.9.9",
                NOW.plus(Duration.ofDays(7)));

        ArgumentCaptor<Session> saved = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).insert(saved.capture());
        Session session = saved.getValue();
        assertThat(session.getId()).isEqualTo("sess1");
        assertThat(session.getUserId()).isEqualTo("ABCD2345");
        assertThat(session.getClientId()).isEqualTo("spa");
        assertThat(session.getRefreshTokenHash()).isEqualTo(TokenHasher.hash("secret")).isNotEqualTo("secret");
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(session.getIpAddress()).isEqualTo("9.9.9.9");
    }

    @Test
    @Tag("critical")
    void start_savesNothing_withoutASessionIdOrAClient() {
        assertThatThrownBy(() -> service.start(null, "ABCD2345", "spa", "hash", "ip", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.start("sess1", "ABCD2345", " ", "hash", "ip", NOW))
                .isInstanceOf(IllegalArgumentException.class);

        verify(sessionRepository, never()).insert(any(Session.class));
    }

    @Test
    @Tag("critical")
    void rotateRefreshToken_returnsTheRotatedSession() {
        Session rotated = persisted("sess1", "newhash");
        when(sessionRepository.rotateRefreshToken("spa", "old", "new", NOW)).thenReturn(rotated);

        assertThat(service.rotateRefreshToken("spa", "old", "new")).isSameAs(rotated);

    }

    @Test
    @Tag("critical")
    void rotateRefreshToken_rejectsATokenNoSessionHolds() {
        when(sessionRepository.rotateRefreshToken(any(), anyString(), anyString(), any())).thenReturn(null);

        assertThatThrownBy(() -> service.rotateRefreshToken("spa", "h", "n"))
                .isInstanceOf(InvalidGrantException.class);
    }

    @Test
    @Tag("critical")
    void revoke_deletesTheSession() {
        service.revoke("sess1");

        verify(sessionRepository).deleteById("sess1");
    }
}
