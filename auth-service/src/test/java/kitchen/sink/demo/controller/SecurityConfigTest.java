package kitchen.sink.demo.controller;

import kitchen.sink.demo.config.SecurityConfig;
import kitchen.sink.demo.dto.response.TokenResponse;
import kitchen.sink.demo.exception.AuthenticationFailedException;
import kitchen.sink.demo.exception.GlobalExceptionHandler;
import kitchen.sink.demo.security.JwtService;
import kitchen.sink.demo.service.AuthService;
import kitchen.sink.demo.service.RegistrationService;
import kitchen.sink.demo.config.JwtConfig;
import kitchen.sink.demo.config.JwtKeys;
import kitchen.sink.demo.config.JwtProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs the real filter chain: which routes are public, how tokens are validated, how auth errors look. */
@WebMvcTest({ProtectedProbeController.class, AuthController.class, RegistrationController.class, OAuthController.class})
@ContextConfiguration(classes = {ProtectedProbeController.class, AuthController.class, RegistrationController.class, OAuthController.class,
        GlobalExceptionHandler.class, SecurityConfig.class, JwtConfig.class, JwtService.class})
@TestPropertySource(locations = "classpath:jwt-test.properties")
class SecurityConfigTest {

    static final String AUD = "auth-service";

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired JwtDecoder jwtDecoder;

    @MockitoBean RegistrationService registrationService;
    @MockitoBean AuthService authService;
    @MockitoBean kitchen.sink.demo.service.TokenExchangeService tokenExchangeService;
    @MockitoBean kitchen.sink.demo.service.AuthorizationCodeService authorizationCodeService;
    @MockitoBean kitchen.sink.demo.service.grant.RefreshTokenGrantHandler refreshTokenService;

    private static final String LOGIN = "{\"email\":\"ann@example.com\",\"password\":\"s3cretPassphrase15\",\"requestId\":\"req-1\"}";

    @Test
    @Tag("critical")
    void protectedEndpoint_require_authentication() throws Exception {
        mockMvc.perform(get("/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/probe/x")).andExpect(status().isUnauthorized());
    }

    @Test
    @Tag("critical")
    void protectedEndpoint_rejectGarbageToken() throws Exception {
        mockMvc.perform(get("/probe").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @Tag("critical")
    void protectedEndpoint_acceptRealIssuedAccessToken() throws Exception {
        String token = jwtService.issueAccessToken("ann@example.com", "sess1", java.util.List.of(AUD), "spa", "1.2.3.4", Instant.now(), Instant.now().plus(Duration.ofDays(7)));

        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @Tag("critical")
    void issuedAccessToken_carriesTheClientsAudiencesAndAzpIsTheClientId() {
        String token = jwtService.issueAccessToken("ann@example.com", "sess1", java.util.List.of("api", AUD),
                "spa", "1.2.3.4", Instant.now(), Instant.now().plus(Duration.ofDays(7)));

        org.springframework.security.oauth2.jwt.Jwt jwt = jwtDecoder.decode(token);
        org.assertj.core.api.Assertions.assertThat(jwt.getAudience()).containsExactly("api", AUD);
        org.assertj.core.api.Assertions.assertThat(jwt.getClaimAsString("azp")).isEqualTo("spa");
    }

    @Test
    @Tag("critical")
    void accessToken_withoutThisServiceInItsAudience_isRejected() throws Exception {
        String token = jwtService.issueAccessToken("ann@example.com", "sess1", java.util.List.of("api", "billing"),
                "spa", "1.2.3.4", Instant.now(), Instant.now().plus(Duration.ofDays(7)));

        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Tag("critical")
    void expiredAccessToken_isRejected() throws Exception {
        // issued 1 hour ago with a 15 minute TTL
        String token = jwtService.issueAccessToken("ann@example.com", "sess1", java.util.List.of(AUD), "spa", "1.2.3.4", Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofDays(7)));

        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Tag("critical")
    void accessTokenAudience_isTheClientName() {
        String token = jwtService.issueAccessToken("ann@example.com", "sess1", java.util.List.of(AUD), "spa", "1.2.3.4", Instant.now(), Instant.now().plus(Duration.ofDays(7)));

        org.assertj.core.api.Assertions.assertThat(jwtDecoder.decode(token).getAudience()).containsExactly(AUD);
    }

    @Test
    @Tag("critical")
    void tokenIssuedWithoutAClient_hasNoAudienceAndIsRejected() throws Exception {
        String token = jwtService.issueAccessToken("ann@example.com", "sess1", null, "spa", "1.2.3.4", Instant.now(), Instant.now().plus(Duration.ofDays(7)));

        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Tag("critical")
    void tokenWithoutAudience_isRejected() throws Exception {
        org.springframework.security.oauth2.jwt.JwtClaimsSet claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer("http://localhost:8080/auth-service").subject("ann@example.com")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        String token = jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(
                        org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build(), claims)).getTokenValue();

        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Tag("critical")
    void tokenSignedWithAnotherKey_isRejected() throws Exception {
        java.security.KeyPairGenerator gen = java.security.KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        java.security.KeyPair pair = gen.generateKeyPair();
        JwtService other = new JwtService(
                new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(new JwtKeys(
                        (java.security.interfaces.RSAPrivateKey) pair.getPrivate(),
                        (java.security.interfaces.RSAPublicKey) pair.getPublic()).jwkSource()),
                new JwtProperties(null, null, "http://localhost:8080/auth-service", null, null, null));
        String forged = other.issueAccessToken("ann@example.com", "sess1", java.util.List.of(AUD), "spa", "1.2.3.4", Instant.now(), Instant.now().plus(Duration.ofDays(7)));

        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Tag("critical")
    void oauthAuthorize_isPublic_andRedirectsToTheLoginPage() throws Exception {
        when(authorizationCodeService.authorize(any())).thenReturn("http://localhost:3000/login?request_id=r1");

        mockMvc.perform(get("/oauth2/authorize").param("response_type", "code").param("client_id", "spa")
                        .param("redirect_uri", "https://app.example.com/cb")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:3000/login?request_id=r1"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void oauthAuthorize_returns400_whenClientOrRedirectUriIsNotRegistered() throws Exception {
        when(authorizationCodeService.authorize(any()))
                .thenThrow(new kitchen.sink.demo.exception.InvalidAuthorizationRequestException("Unknown client or redirect URI"));

        mockMvc.perform(get("/oauth2/authorize").param("client_id", "evil"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Tag("critical")
    void oauthToken_isPublic_andReturnsTokens() throws Exception {
        when(tokenExchangeService.exchange(any(), any())).thenReturn(new TokenResponse("a", "Bearer", 900, "s.r", "s"));

        mockMvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grantType\":\"authorization_code\",\"code\":\"c0de\",\"clientId\":\"spa\",\"codeVerifier\":\"dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.accessToken").value("a"));
    }

    @Test
    @Tag("critical")
    void oauthToken_acceptsTheRefreshGrantWithoutCodeParameters() throws Exception {
        when(tokenExchangeService.exchange(any(), any())).thenReturn(new TokenResponse("a2", "Bearer", 900, "s.r2", "s"));

        mockMvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grantType\":\"refresh_token\",\"clientId\":\"spa\",\"refreshToken\":\"s.r\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("a2"));
    }

    @Test
    @Tag("critical")
    void oauthToken_returns400_whenVerifierMalformedOrGrantRefused() throws Exception {
        mockMvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grantType\":\"authorization_code\",\"code\":\"c0de\",\"clientId\":\"spa\",\"codeVerifier\":\"short\"}"))
                .andExpect(status().isBadRequest());

        when(tokenExchangeService.exchange(any(), any()))
                .thenThrow(new kitchen.sink.demo.exception.InvalidGrantException("Invalid or expired authorization code"));
        mockMvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grantType\":\"authorization_code\",\"code\":\"c0de\",\"clientId\":\"spa\",\"codeVerifier\":\"dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired authorization code"));
    }


    @Test
    void logout_requiresAuth_andPassesTheBearerTokenToTheAuthService() throws Exception {
        mockMvc.perform(post("/auth/logout")).andExpect(status().isUnauthorized());

        String token = jwtService.issueAccessToken("ann@example.com", "sess1", java.util.List.of(AUD), "spa", "1.2.3.4", Instant.now(), Instant.now().plus(Duration.ofDays(7)));
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        verify(authService).logout(token);
    }

    @Test
    @Tag("critical")
    void accessTokenCarriesTheSessionIdAndTheClientIpAddress() {
        String token = jwtService.issueAccessToken("ann@example.com", "sess1", java.util.List.of(AUD), "spa", "203.0.113.7", Instant.now(), Instant.now().plus(Duration.ofDays(7)));

        var jwt = jwtDecoder.decode(token);

        org.assertj.core.api.Assertions.assertThat(jwt.getClaimAsString("sid")).isEqualTo("sess1");
        org.assertj.core.api.Assertions.assertThat(jwt.getClaimAsString("ip")).isEqualTo("203.0.113.7");
    }
}
