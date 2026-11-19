package kitchen.sink.demo.controller;

import kitchen.sink.demo.config.JwtConfig;
import kitchen.sink.demo.config.SecurityConfig;
import kitchen.sink.demo.dto.request.LoginRequest;
import kitchen.sink.demo.dto.response.AuthorizationCodeResponse;
import kitchen.sink.demo.exception.AuthenticationFailedException;
import kitchen.sink.demo.exception.InvalidAuthorizationRequestException;
import kitchen.sink.demo.security.JwtService;
import kitchen.sink.demo.service.AuthService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The hosted /login and /register pages, run through the real filter chain (they must be public, no CSRF token). */
@WebMvcTest(LoginPageController.class)
@ContextConfiguration(classes = {LoginPageController.class, SecurityConfig.class, JwtConfig.class, JwtService.class})
@TestPropertySource(locations = "classpath:jwt-test.properties")
class LoginPageControllerTest {

    private static final String PASSWORD = "s3cretPassphrase15";

    @Autowired MockMvc mockMvc;
    @MockitoBean AuthService authService;

    @Test
    void loginPage_withRequest_rendersFormWithEscapedRequestId() throws Exception {
        mockMvc.perform(get("/login").param("request_id", "<b>\"x"))
                .andExpect(status().isOk()).andExpect(view().name("login"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"request_id\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<b>\"x"))));
    }

    @Test
    void loginPage_withoutRequest_showsMessageAndNoForm() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("must be started from an application")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<form"))));
    }

    @Test
    void loginPage_expired_hidesForm() throws Exception {
        mockMvc.perform(get("/login").param("request_id", "r1").param("error", "expired"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("has expired")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<form"))));
    }

    @Test
    void registerPage_isPublic_andLinksBackToLogin() throws Exception {
        mockMvc.perform(get("/register").param("request_id", "r1"))
                .andExpect(status().isOk()).andExpect(view().name("register"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/login?request_id=r1")));
    }

    @Test
    void pages_sendContentSecurityPolicy() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("script-src 'self'")));
    }

    @Test
    @Tag("critical")
    void login_correctPassword_redirectsToClientWithCode() throws Exception {
        when(authService.login(any(LoginRequest.class), any()))
                .thenReturn(new AuthorizationCodeResponse("https://app.example/cb?code=abc&iss=x", 60));

        mockMvc.perform(post("/login").contentType("application/x-www-form-urlencoded")
                        .param("email", "ann@example.com").param("password", PASSWORD).param("request_id", "req-1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://app.example/cb?code=abc&iss=x"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    @Tag("critical")
    void login_wrongPassword_goesBackToLoginPage() throws Exception {
        when(authService.login(any(LoginRequest.class), any()))
                .thenThrow(new AuthenticationFailedException("Invalid email or password"));

        mockMvc.perform(post("/login").contentType("application/x-www-form-urlencoded")
                        .param("email", "ann@example.com").param("password", PASSWORD).param("request_id", "req 1&x"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/login?request_id=req%201%26x&error=invalid"));
    }

    @Test
    void login_expiredRequest_goesBackWithExpiredError() throws Exception {
        when(authService.login(any(LoginRequest.class), any()))
                .thenThrow(new InvalidAuthorizationRequestException("expired"));

        mockMvc.perform(post("/login").contentType("application/x-www-form-urlencoded")
                        .param("email", "ann@example.com").param("password", PASSWORD).param("request_id", "req-1"))
                .andExpect(header().string("Location", "/login?request_id=req-1&error=expired"));
    }

    @Test
    void login_invalidInput_isRefusedBeforeAuthService() throws Exception {
        mockMvc.perform(post("/login").contentType("application/x-www-form-urlencoded")
                        .param("email", "not-an-email").param("password", "short").param("request_id", "req-1"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/login?request_id=req-1&error=invalid_input"));
        verify(authService, never()).login(any(), eq("127.0.0.1"));
    }

    @Test
    void login_oversizedRequestId_isNotEchoed() throws Exception {
        mockMvc.perform(post("/login").contentType("application/x-www-form-urlencoded")
                        .param("email", "ann@example.com").param("password", PASSWORD).param("request_id", "x".repeat(300)))
                .andExpect(header().string("Location", "/login?error=invalid_input"));
    }
}
