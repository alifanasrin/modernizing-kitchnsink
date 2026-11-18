package kitchen.sink.demo.service.impl;

import kitchen.sink.demo.dto.request.AuthorizationRequest;
import kitchen.sink.demo.dto.response.AuthorizationCodeResponse;
import kitchen.sink.demo.entity.AuthorizationGrant;
import kitchen.sink.demo.entity.OAuthClient;
import kitchen.sink.demo.exception.InvalidAuthorizationRequestException;
import kitchen.sink.demo.repository.AuthorizationGrantStore;
import kitchen.sink.demo.repository.OAuthClientRepository;
import kitchen.sink.demo.service.AuthorizationCodeService;
import kitchen.sink.demo.util.Pkce;
import kitchen.sink.demo.util.TokenHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

@Service
public class AuthorizationCodeServiceImpl implements AuthorizationCodeService {

    static final String BAD_CLIENT = "Unknown client or redirect URI";
    static final String BAD_REQUEST = "Unknown or expired authorization request";
    /** How long a code can be redeemed; short on purpose. */
    static final Duration CODE_TTL = Duration.ofSeconds(60);
    /** How long a request may wait for the login. */
    static final Duration PENDING_TTL = Duration.ofMinutes(5);

    private static final Pattern CHALLENGE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Logger log = LoggerFactory.getLogger(AuthorizationCodeServiceImpl.class);

    private final OAuthClientRepository clients;
    private final AuthorizationGrantStore grants;
    private final Clock clock;
    private final ClientRedirects redirects;
    private final String loginUrl;

    public AuthorizationCodeServiceImpl(OAuthClientRepository clients, AuthorizationGrantStore grants, Clock clock,
                                        ClientRedirects redirects, @Value("${app.oauth.login-url}") String loginUrl) {
        this.clients = clients;
        this.grants = grants;
        this.clock = clock;
        this.redirects = redirects;
        this.loginUrl = loginUrl;
    }

    /** Validates the request, stores it under a new request id and returns the login page URL (or an error redirect). */
    @Override
    public String authorize(AuthorizationRequest request) {
        // without a trusted redirect URI an error must not be redirected anywhere
        boolean registered = request.clientId() != null && request.redirectUri() != null
                && clients.findByClientId(request.clientId()).map(c -> c.allows(request.redirectUri())).orElse(false);
        if (!registered) {
            log.warn("authorization request refused for client {}", request.clientId());
            throw new InvalidAuthorizationRequestException(BAD_CLIENT);
        }
        if (!"code".equals(request.responseType())) {
            return errorRedirect(request, "unsupported_response_type", "response_type must be code");
        }
        if (request.codeChallenge() == null || !CHALLENGE.matcher(request.codeChallenge()).matches()) {
            return errorRedirect(request, "invalid_request", "code_challenge must be a base64url SHA-256 (43 characters)");
        }
        if (!Pkce.S256.equals(request.codeChallengeMethod())) {
            return errorRedirect(request, "invalid_request", "code_challenge_method must be S256");
        }
        Instant now = clock.instant();
        String requestId = TokenHasher.newSecret();
        grants.save(new AuthorizationGrant(requestId, request.clientId(), request.redirectUri(),
                request.codeChallenge(), request.state(), now, now.plus(PENDING_TTL)));
        log.info("authorization request created for client {}", request.clientId());
        return UriComponentsBuilder.fromUriString(loginUrl).queryParam("request_id", requestId).build().toUriString();
    }

    /** Refuses a login whose request id is unknown, expired or already used. */
    @Override
    public void validate(String requestId) {
        if (grants.findOpen(requestId, clock.instant()).isEmpty()) {
            log.warn("login refused, authorization request is unknown, expired or already used");
            throw new InvalidAuthorizationRequestException(BAD_REQUEST);
        }
    }

    /** Creates the code, stores only its hash and returns the redirect to the client carrying the code once. */
    @Override
    public AuthorizationCodeResponse complete(String requestId, String userId) {
        Instant now = clock.instant();
        String code = TokenHasher.newSecret();
        AuthorizationGrant grant = grants.issueCode(requestId, userId, TokenHasher.hash(code), now,
                now.plus(CODE_TTL)).orElseThrow(() -> {
            log.warn("authorization code not issued to user {}, request is unknown, expired or already issued", userId);
            return new InvalidAuthorizationRequestException(BAD_REQUEST);
        });
        log.info("authorization code issued to client {} for user {}", grant.getClientId(), userId);
        String redirectTo = redirects.success(grant.getRedirectUri(), code, grant.getState());
        return new AuthorizationCodeResponse(redirectTo, CODE_TTL.toSeconds());
    }

    private String errorRedirect(AuthorizationRequest request, String error, String description) {
        log.warn("authorization request from client {} refused: {}", request.clientId(), error);
        return redirects.error(request.redirectUri(), error, description, request.state());
    }
}
