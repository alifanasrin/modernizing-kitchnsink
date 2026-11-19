package kitchen.sink.member.config;

import kitchen.sink.member.security.RestAuthenticationHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Stateless bearer-JWT security: every endpoint needs a valid auth-service access token, except the health check. */
@Configuration
public class SecurityConfig {

    /** Defines the filter chain; only {@code /actuator/health} is public. */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Bearer tokens are not sent by browsers automatically, so CSRF does not apply.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(RestAuthenticationHandlers.entryPoint())
                        .accessDeniedHandler(RestAuthenticationHandlers.accessDeniedHandler()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(RestAuthenticationHandlers.entryPoint())
                        .accessDeniedHandler(RestAuthenticationHandlers.accessDeniedHandler()));
        return http.build();
    }
}
