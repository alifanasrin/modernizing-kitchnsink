package kitchen.sink.demo.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import kitchen.sink.demo.dto.request.LoginRequest;
import kitchen.sink.demo.dto.response.AuthorizationCodeResponse;
import kitchen.sink.demo.exception.AuthenticationFailedException;
import kitchen.sink.demo.exception.InvalidAuthorizationRequestException;
import kitchen.sink.demo.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Hosts the browser pages ({@code /login}, {@code /register}) as Thymeleaf templates. The login form is a plain browser navigation, because the
 * authorization code comes back as a 302 to the client that a {@code fetch} could not follow. A failure sends the
 * browser back to {@code /login?request_id=...&error=...}; the JSON API stays {@code POST /auth/login}.
 */
@Controller
public class LoginPageController {

    private static final Logger log = LoggerFactory.getLogger(LoginPageController.class);
    private static final int MAX_REQUEST_ID = 128;
    private static final Map<String, String> ERRORS = Map.of(
            "invalid", "Invalid email or password.",
            "invalid_input", "Enter a valid email and a password of 15 to 64 characters.",
            "expired", "This sign-in request has expired. Please go back to the application and start again.");

    private final AuthService authService;
    private final Validator validator;

    public LoginPageController(AuthService authService, Validator validator) {
        this.authService = authService;
        this.validator = validator;
    }

    /** Shows the form for an open authorization request; with no (or an expired) request it shows only the message. */
    @GetMapping("/login")
    String loginPage(@RequestParam(name = "request_id", required = false) String requestId,
                     @RequestParam(name = "error", required = false) String error, Model model) {
        boolean expired = "expired".equals(error);
        boolean hasRequest = requestId != null && !requestId.isBlank() && requestId.length() <= MAX_REQUEST_ID;
        model.addAttribute("requestId", hasRequest && !expired ? requestId : null);
        model.addAttribute("error", error != null ? ERRORS.getOrDefault(error, "Sign-in failed. Please try again.")
                : hasRequest ? null : "Sign-in must be started from an application.");
        return "login";
    }

    @GetMapping("/register")
    String registerPage(@RequestParam(name = "request_id", required = false) String requestId, Model model) {
        boolean valid = requestId != null && !requestId.isBlank() && requestId.length() <= MAX_REQUEST_ID;
        model.addAttribute("requestId", valid ? requestId : null);
        return "register";
    }

    /** Checks the password and answers 302 to the client's redirect URI with the code, or back to the login page. */
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<Void> login(@RequestParam(name = "email", defaultValue = "") String email,
                               @RequestParam(name = "password", defaultValue = "") String password,
                               @RequestParam(name = "request_id", defaultValue = "") String requestId,
                               HttpServletRequest http) {
        log.debug("login form submitted from {}", http.getRemoteAddr());
        LoginRequest request = new LoginRequest(email.trim(), password, requestId);
        if (!validator.validate(request).isEmpty()) {
            log.warn("login form refused: invalid input from {}", http.getRemoteAddr());
            return back(requestId, "invalid_input");
        }
        try {
            AuthorizationCodeResponse code = authService.login(request, http.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, code.redirectTo())
                    .cacheControl(CacheControl.noStore()).build();
        } catch (AuthenticationFailedException e) {
            return back(requestId, "invalid");
        } catch (InvalidAuthorizationRequestException e) {
            return back(requestId, "expired");
        }
    }

    /** 303 to the login page; the request id is echoed only while it is a plausible id, and always encoded. */
    private static ResponseEntity<Void> back(String requestId, String error) {
        UriComponentsBuilder target = UriComponentsBuilder.fromPath("/login");
        if (!requestId.isBlank() && requestId.length() <= MAX_REQUEST_ID) {
            target.queryParam("request_id", requestId);
        }
        String location = target.queryParam("error", error).encode().build().toUriString();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, location)
                .cacheControl(CacheControl.noStore()).build();
    }
}
