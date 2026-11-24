package kitchen.sink.member.controller;

import kitchen.sink.member.TestTokens;
import kitchen.sink.member.config.JwtConfig;
import kitchen.sink.member.config.JwtProperties;
import kitchen.sink.member.config.SecurityConfig;
import kitchen.sink.member.dto.response.MemberResponse;
import kitchen.sink.member.dto.response.PaginatedResponse;
import kitchen.sink.member.exception.GlobalExceptionHandler;
import kitchen.sink.member.service.MemberService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static kitchen.sink.member.TestTokens.AUDIENCE;
import static kitchen.sink.member.TestTokens.ISSUER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs the real filter chain and the real decoder: only auth-service tokens for this service get through. */
@WebMvcTest(MemberController.class)
@ContextConfiguration(classes = {MemberController.class, GlobalExceptionHandler.class, SecurityConfig.class,
        JwtConfig.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(locations = "classpath:jwt-test.properties")
class SecurityConfigTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean MemberService memberService;

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    @Tag("critical")
    void everyMemberEndpoint_requiresAToken() throws Exception {
        mockMvc.perform(get("/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/members/ann@example.com")).andExpect(status().isUnauthorized());
        verifyNoInteractions(memberService);
    }

    /** Temporary: replace with a service-client permission check (see the note on MemberController#createMember). */
    @Test
    void postMembers_isOpenUntilServiceAccountTokensExist_butReadsStayProtected() throws Exception {
        when(memberService.createMember(any()))
                .thenReturn(new MemberResponse("Ann", "Lee", "1234567890", "ann@example.com"));

        mockMvc.perform(post("/members").contentType("application/json")
                        .content("{\"email\":\"ann@example.com\",\"phoneNo\":\"1234567890\",\"firstName\":\"Ann\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/members")).andExpect(status().isUnauthorized());
    }

    @Test
    @Tag("critical")
    void aValidAuthServiceToken_isAccepted() throws Exception {
        when(memberService.getMembers(any())).thenReturn(new PaginatedResponse<>(
                List.of(new MemberResponse("Ann", "Lee", "1234567890", "ann@example.com")), 10, 1, 1, 1));

        mockMvc.perform(get("/members").header("Authorization", bearer(TestTokens.valid())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value("ann@example.com"));
    }

    @Test
    @Tag("critical")
    void garbageToken_isRejected() throws Exception {
        mockMvc.perform(get("/members").header("Authorization", bearer("not.a.jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Tag("critical")
    void tokenSignedByAnotherKey_isRejected() throws Exception {
        mockMvc.perform(get("/members").header("Authorization", bearer(TestTokens.forged())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Tag("critical")
    void expiredToken_isRejected() throws Exception {
        String expired = TestTokens.token(ISSUER, List.of(AUDIENCE),
                Instant.now().minus(Duration.ofHours(1)), Duration.ofMinutes(15));

        mockMvc.perform(get("/members").header("Authorization", bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Tag("critical")
    void tokenOfAnotherIssuer_isRejected() throws Exception {
        String token = TestTokens.token("http://evil.example", List.of(AUDIENCE), Instant.now(), Duration.ofMinutes(15));

        mockMvc.perform(get("/members").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Tag("critical")
    void tokenWhoseAudienceDoesNotNameThisService_isRejected() throws Exception {
        String token = TestTokens.token(ISSUER, List.of("auth-service"), Instant.now(), Duration.ofMinutes(15));

        mockMvc.perform(get("/members").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Tag("critical")
    void tokenWithoutAudience_isRejected() throws Exception {
        String token = TestTokens.token(ISSUER, null, Instant.now(), Duration.ofMinutes(15));

        mockMvc.perform(get("/members").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }
}
