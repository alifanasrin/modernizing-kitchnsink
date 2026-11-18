package kitchen.sink.demo.config;

import kitchen.sink.demo.entity.Member;
import kitchen.sink.demo.repository.MemberRepository;
import kitchen.sink.demo.security.MemberPrincipal;
import kitchen.sink.demo.security.MemberUserDetailsService;
import kitchen.sink.demo.security.Sha256BcryptPasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The real provider chain (user details service, provider, the project's password encoder) over a mocked repository. */
class AuthenticationConfigTest {

    private final MemberRepository members = mock(MemberRepository.class);
    private final PasswordEncoder encoder = new Sha256BcryptPasswordEncoder();
    private AuthenticationManager manager;

    @BeforeEach
    void setUp() {
        manager = new AuthenticationConfig().authenticationManager(new MemberUserDetailsService(members), encoder);
    }

    private static UsernamePasswordAuthenticationToken credentials(String email, String password) {
        return UsernamePasswordAuthenticationToken.unauthenticated(email, password);
    }

    @Test
    @Tag("critical")
    void correctPassword_returnsThePrincipalWithTheMember() {
        Member member = new Member("ann@example.com", "1234567890", "Ann", "Lee", encoder.encode("s3cretPassword!"));
        when(members.findByEmail("ann@example.com")).thenReturn(Optional.of(member));

        Authentication result = manager.authenticate(credentials("Ann@Example.com", "s3cretPassword!"));

        assertThat(((MemberPrincipal) result.getPrincipal()).member()).isSameAs(member);
    }

    @Test
    @Tag("critical")
    void wrongPassword_unknownEmail_andMemberWithoutPassword_areAllBadCredentials() {
        Member member = new Member("ann@example.com", "1234567890", "Ann", "Lee", encoder.encode("s3cretPassword!"));
        Member legacy = new Member("old@example.com", "1234567890", "Old", "Lee");
        when(members.findByEmail("ann@example.com")).thenReturn(Optional.of(member));
        when(members.findByEmail("old@example.com")).thenReturn(Optional.of(legacy));
        when(members.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        for (String[] attempt : new String[][]{{"ann@example.com", "wrong"}, {"nobody@example.com", "whatever1"},
                {"old@example.com", "s3cretPassword!"}}) {
            assertThatThrownBy(() -> manager.authenticate(credentials(attempt[0], attempt[1])))
                    .isExactlyInstanceOf(BadCredentialsException.class);
        }
    }

}
