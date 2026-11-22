package kitchen.sink.demo.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kitchen.sink.demo.dto.request.LoginRequest;
import kitchen.sink.demo.dto.response.AuthorizationCodeResponse;
import kitchen.sink.demo.enumeration.LoginError;
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
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Hosts the browser pages ({@code /login}, {@code /register}) as Thymeleaf templates. The login form is a plain browser navigation, because the
 * authorization code comes back as a 302 to the client that a {@code fetch} could not follow. A failure sends the
 * browser back to {@code /login?request_id=...&error=...}; the old JSON {@code POST /auth/login} no longer exists.
 */
@Controller
public class PageController {

    private static final Logger log = LoggerFactory.getLogger(PageController.class);
    private static final int MAX_REQUEST_ID = 128;

    private final AuthService authService;

    public PageController(AuthService authService) {
        this.authService = authService;
    }

    /** Shows the form for an open authorization request; with no (or an expired) request it shows only the message. */
    @GetMapping("/login")
    String loginPage(@RequestParam(name = "request_id", required = false) String requestId,
                     @RequestParam(name = "error", required = false) String error, Model model) {
        boolean hasRequest = isPlausible(requestId);
        boolean expired = LoginError.EXPIRED.code().equals(error);
        String message = error != null
                ? LoginError.fromCode(error).map(LoginError::message).orElse("Sign-in failed. Please try again.")
                : hasRequest ? null : "Sign-in must be started from an application.";
        model.addAttribute("requestId", hasRequest && !expired ? requestId : null);
        model.addAttribute("error", message);
        return "login";
    }

    @GetMapping("/register")
    String registerPage(@RequestParam(name = "request_id", required = false) String requestId, Model model) {
        model.addAttribute("requestId", isPlausible(requestId) ? requestId : null);
        return "register";
    }

    /** Checks the password and answers 302 to the client's redirect URI with the code, or back to the login page. */
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<Void> login(@Valid @ModelAttribute("loginRequest") LoginRequest request, BindingResult errors,
                               HttpServletRequest http) {
        log.debug("login form submitted from {}", http.getRemoteAddr());
        if (errors.hasErrors()) {
            log.warn("login form refused: invalid input from {}", http.getRemoteAddr());
            return back(request.requestId(), LoginError.INVALID_INPUT);
        }
        try {
            AuthorizationCodeResponse code = authService.login(request, http.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, code.redirectTo())
                    .cacheControl(CacheControl.noStore()).build();
        } catch (AuthenticationFailedException e) {
            return back(request.requestId(), LoginError.INVALID);
        } catch (InvalidAuthorizationRequestException e) {
            return back(request.requestId(), LoginError.EXPIRED);
        }
    }

    private static boolean isPlausible(String requestId) {
        return requestId != null && !requestId.isBlank() && requestId.length() <= MAX_REQUEST_ID;
    }

    /** 303 to the login page; the request id is echoed only while it is a plausible id, and always encoded. */
    private static ResponseEntity<Void> back(String requestId, LoginError error) {
        UriComponentsBuilder target = UriComponentsBuilder.fromPath("/login");
        if (isPlausible(requestId)) {
            target.queryParam("request_id", requestId);
        }
        String location = target.queryParam("error", error.code()).encode().build().toUriString();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, location)
                .cacheControl(CacheControl.noStore()).build();
    }
}
