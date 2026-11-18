package kitchen.sink.demo.security;

import kitchen.sink.demo.entity.Member;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The Spring Security view of a {@link Member}. It keeps the member so the login code gets it back from the
 * authentication result without a second lookup.
 * <p>
 * The account checks ({@code isEnabled()} and the others) keep the interface default of true.
 */
public record MemberPrincipal(Member member) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return member.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return member.getEmail();
    }

    /** Hides the member (and so the password hash) from logs. */
    @Override
    public String toString() {
        return "MemberPrincipal[id=" + member.getId() + "]";
    }
}
