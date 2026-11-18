package kitchen.sink.demo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kitchen.sink.demo.dto.request.AuthorizationRequest;
import kitchen.sink.demo.dto.request.TokenRequest;
import kitchen.sink.demo.dto.response.TokenResponse;
import kitchen.sink.demo.service.AuthorizationCodeService;
import kitchen.sink.demo.service.TokenExchangeService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oauth2")
public class OAuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);

    private final TokenExchangeService tokenExchange;
    private final AuthorizationCodeService authorizationCodes;

    public OAuthController(TokenExchangeService tokenExchange, AuthorizationCodeService authorizationCodes) {
        this.tokenExchange = tokenExchange;
        this.authorizationCodes = authorizationCodes;
    }

    /**
     * The authorization endpoint (RFC 6749 section 3.1, PKCE RFC 7636). The client sends the user's browser here; the
     * answer is a 302 to the login page, or to the client's redirect URI with an {@code error}. An unknown client or
     * unregistered redirect URI answers 400 and is never redirected to.
     */
    @GetMapping("/authorize")
    ResponseEntity<Void> authorize(
            @RequestParam(name = "response_type", required = false) @Size(max = 32) String responseType,
            @RequestParam(name = "client_id", required = false) @Size(max = 128) String clientId,
            @RequestParam(name = "redirect_uri", required = false) @Size(max = 2048) String redirectUri,
            @RequestParam(name = "code_challenge", required = false) @Size(max = 128) String codeChallenge,
            @RequestParam(name = "code_challenge_method", required = false) @Size(max = 16) String codeChallengeMethod,
            // RFC 6749 appendix A.5: state is 1*VSCHAR (printable ASCII)
            @RequestParam(name = "state", required = false) @Size(max = 512)
            @Pattern(regexp = "[\\x20-\\x7E]*", message = "state must be printable ASCII") String state) {
        log.debug("authorize request from client {}", clientId);
        String location = authorizationCodes.authorize(new AuthorizationRequest(responseType, clientId, redirectUri,
                codeChallenge, codeChallengeMethod, state));
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location)
                .cacheControl(CacheControl.noStore()).build();
    }

    /**
     * The token endpoint. {@code grantType} selects the flow: {@code authorization_code} (code,
     * codeVerifier) or {@code refresh_token} (refreshToken); the handler of the grant checks its own parameters.
     * The values travel in a JSON body, like every other endpoint of this service.
     */
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TokenResponse> token(@Valid @RequestBody TokenRequest request, HttpServletRequest http) {
        log.debug("token request from client {} at {}", request.clientId(), http.getRemoteAddr());
        TokenResponse tokens = tokenExchange.exchange(request, http.getRemoteAddr());
        // RFC 6749 section 5.1: token responses must not be cached
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(tokens);
    }
}
