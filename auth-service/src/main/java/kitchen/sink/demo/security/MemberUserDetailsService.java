package kitchen.sink.demo.security;

import kitchen.sink.demo.entity.Member;
import kitchen.sink.demo.repository.MemberRepository;
import kitchen.sink.demo.util.EmailNormalizer;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads a member by email for the password check. The email is normalized the same way as at registration. */
@Service
public class MemberUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    public MemberUserDetailsService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * Finds the member. One without a password (registered before passwords existed) is treated as unknown, so it
     * gets the same refusal and the same dummy password comparison as an email that is not registered.
     */
    @Override
    public UserDetails loadUserByUsername(String email) {
        Member member = memberRepository.findByEmail(EmailNormalizer.normalize(email))
                .filter(m -> m.getPasswordHash() != null)
                .orElseThrow(() -> new UsernameNotFoundException("no member with a password for this email"));
        return new MemberPrincipal(member);
    }
}
