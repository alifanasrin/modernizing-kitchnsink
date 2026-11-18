package kitchen.sink.demo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kitchen.sink.demo.dto.request.LoginRequest;
import kitchen.sink.demo.dto.response.AuthorizationCodeResponse;
import kitchen.sink.demo.service.AuthService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Checks the request and the password and sends the browser to the client's registered redirect URI with the
     * one-time authorization code: a 302 whose {@code Location} is {@code redirect_uri?code=...&state=...&iss=...}.
     * No tokens are issued here (see /oauth2/token).
     */
    @PostMapping("/login")
    ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        log.debug("login request from {}", http.getRemoteAddr());
        AuthorizationCodeResponse code = authService.login(request, http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, code.redirectTo())
                .cacheControl(CacheControl.noStore()).build();
    }

    /** Revokes the session that the bearer access token in the Authorization header belongs to. */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        log.debug("logout request");
        authService.logout(authorization.replaceFirst("(?i)^Bearer\s+", "").trim());
        return ResponseEntity.noContent().build();
    }



}
