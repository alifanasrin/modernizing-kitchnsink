package kitchen.sink.demo.service.grant;

import kitchen.sink.demo.config.JwtProperties;
import kitchen.sink.demo.constant.Constant;
import kitchen.sink.demo.dto.request.TokenRequest;
import kitchen.sink.demo.dto.response.TokenResponse;
import kitchen.sink.demo.entity.AuthorizationGrant;
import kitchen.sink.demo.entity.Member;
import kitchen.sink.demo.entity.OAuthClient;
import kitchen.sink.demo.exception.InvalidGrantException;
import kitchen.sink.demo.repository.AuthorizationGrantStore;
import kitchen.sink.demo.repository.MemberRepository;
import kitchen.sink.demo.repository.OAuthClientRepository;
import kitchen.sink.demo.service.SessionService;
import kitchen.sink.demo.security.AccessTokenIssuer;
import kitchen.sink.demo.util.Pkce;
import kitchen.sink.demo.util.TokenHasher;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** {@code authorization_code}: swaps the one-time code plus PKCE verifier for a new session's tokens. */
@Service
public class AuthorizationCodeGrantHandler implements GrantHandler {

    public static final String GRANT_TYPE = "authorization_code";

    private static final Logger log = LoggerFactory.getLogger(AuthorizationCodeGrantHandler.class);

    private final AuthorizationGrantStore grants;
    private final OAuthClientRepository clients;
    private final MemberRepository memberRepository;
    private final SessionService sessionService;
    private final AccessTokenIssuer accessTokens;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AuthorizationCodeGrantHandler(AuthorizationGrantStore grants, OAuthClientRepository clients,
                                         MemberRepository memberRepository, SessionService sessionService,
                                         AccessTokenIssuer accessTokens, JwtProperties jwtProperties,
                                         Clock clock) {
        this.grants = grants;
        this.clients = clients;
        this.memberRepository = memberRepository;
        this.sessionService = sessionService;
        this.accessTokens = accessTokens;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    @Override
    public String grantType() {
        return GRANT_TYPE;
    }

    @Override
    public void validateMandatoryParams(TokenRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new InvalidGrantException(Constant.BAD_GRANT);
        }
        if (request.codeVerifier() == null || request.codeVerifier().isBlank()) {
            throw new InvalidGrantException(Constant.BAD_GRANT);
        }
    }

    /**
     * Deletes the code first, then checks client and PKCE, so a wrong verifier burns the code and cannot
     * be guessed again.
     */
    @Override
    // a refusal throws, but the deleted code must stay deleted: without noRollbackFor the rollback restores it
    @Transactional(noRollbackFor = InvalidGrantException.class)
    public TokenResponse handle(TokenRequest request, String ipAddress) {
        log.info("authorization code handler service");
        String codeHash = TokenHasher.hash(request.code());
        AuthorizationGrant grant = grants.consumeCode(codeHash, clock.instant())
                .orElseThrow(() -> new InvalidGrantException(Constant.BAD_GRANT));

        if (!grant.getClientId().equals(request.clientId())
                || !Pkce.matches(request.codeVerifier(), grant.getCodeChallenge())) {
            log.warn("token request refused for client {}", request.clientId());
            throw new InvalidGrantException(Constant.BAD_GRANT);
        }
        // the client may have been disabled and the member changed since the code was issued
        OAuthClient client = clients.findByClientId(grant.getClientId())
                .filter(c -> c.allows(grant.getRedirectUri()))
                .orElseThrow(() -> new InvalidGrantException(Constant.BAD_GRANT));

        Member member = memberRepository.findById(grant.getUserId())
                .orElseThrow(() -> new InvalidGrantException(Constant.BAD_GRANT));
        // the tokens are created first, then the session that remembers them is saved
        IssuedTokens issued = issueTokens(member, client, ipAddress);
        sessionService.start(issued.sessionId(), member.getId(), client.getClientId(), issued.refreshTokenHash(),
                ipAddress, issued.sessionExpiresAt());
        grants.attachSession(grant.getId(), issued.response().sessionId());
        return issued.response();
    }

    /**
     * Creates the access token and the refresh token of a new session. The session id is chosen here because the
     * access token names it ({@code sid} claim). The refresh token is only a random secret that names nothing. The
     * audience is the client's aud list and azp its client id. Nothing is stored; the caller saves the session with
     * {@link IssuedTokens#refreshTokenHash()}.
     */
    private IssuedTokens issueTokens(Member member, OAuthClient client, String ipAddress) {
        String sessionId = new ObjectId().toHexString();
        String refreshSecret = TokenHasher.newSecret();
        Instant now = clock.instant();
        // the session lives as long as a refresh token; the access token is capped at that
        Instant sessionExpiresAt = now.plus(jwtProperties.refreshTokenTtl());
        String accessToken = accessTokens.issueAccessToken(member.getEmail(), sessionId, client.getAud(),
                client.getClientId(), ipAddress, now, sessionExpiresAt);
        TokenResponse response = new TokenResponse(accessToken, "Bearer",
                accessTokens.accessTokenTtlSeconds(now, sessionExpiresAt), refreshSecret, sessionId);
        return new IssuedTokens(sessionId, TokenHasher.hash(refreshSecret), sessionExpiresAt, response);
    }

    /**
     * The tokens for the caller and what the session stores of them: its id and the hash of the refresh secret.
     */
    private record IssuedTokens(String sessionId, String refreshTokenHash, Instant sessionExpiresAt,
                                TokenResponse response) {
    }
}
