package kitchen.sink.demo.config;

import kitchen.sink.demo.security.Sha256BcryptPasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/** How passwords are hashed and checked. Kept apart from the web security rules so either can change alone. */
@Configuration
public class PasswordEncoderConfig {

    /**
     * Creates the password encoder. Stored hashes are plain BCrypt strings ({@code $2a$...}) of the SHA-256 pre-hash,
     * with no algorithm label in front. If the scheme ever changes, users reset their password rather than having
     * hashes migrated.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new Sha256BcryptPasswordEncoder();
    }
}
