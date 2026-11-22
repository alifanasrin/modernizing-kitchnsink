package kitchen.sink.demo.config;

import kitchen.sink.demo.security.RestAuthenticationHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless bearer-JWT security. The HTTP session is never used; the "session" concept is the Mongo
 * {@code session} document that anchors the refresh token. Only the endpoints listed below are public; everything
 * else (for example {@code POST /auth/logout}) needs a valid access token.
 */
@Configuration
public class SecurityConfig {

    /** The hosted pages, the authorize step and their static files. */
    private static final String[] PUBLIC_GET = {"/oauth2/authorize", "/login", "/register", "/pages/**"};

    /** Registration, the login form and the token exchange. */
    private static final String[] PUBLIC_POST = {"/register", "/login", "/oauth2/token"};

    private static final String[] PUBLIC_ANY_METHOD = {
            "/actuator/health", "/actuator/health/**",
            "/openapi.yaml", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"};

    /**
     * The hosted pages load only their own scripts and styles and may not be framed. No form-action: the login
     * form's 302 goes to the client's redirect URI, which CSP would count as a form target.
     */
    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'; base-uri 'none'";

    /** Defines the stateless bearer-JWT rules and which endpoints are public. */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Bearer tokens are not sent by browsers automatically, so CSRF does not apply.
                .csrf(AbstractHttpConfigurer::disable)
                .headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        .requestMatchers(PUBLIC_ANY_METHOD).permitAll()
                        .anyRequest().authenticated())
                // 401/403 are written as ErrorResponse JSON, both for bad tokens and for missing ones
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
