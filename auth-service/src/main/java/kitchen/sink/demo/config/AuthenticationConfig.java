package kitchen.sink.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/** The password check: members from the {@link UserDetailsService}, compared with the {@link PasswordEncoder}. */
@Configuration
public class AuthenticationConfig {

    /**
     * Creates the manager used by login. {@code DaoAuthenticationProvider} hides "user not found" behind the same
     * bad-credentials error as a wrong password and compares against a dummy hash for unknown users, so both take
     * about the same time.
     */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
