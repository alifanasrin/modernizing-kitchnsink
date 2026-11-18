package kitchen.sink.demo.service.grant;

import kitchen.sink.demo.constant.Constant;
import kitchen.sink.demo.dto.request.TokenRequest;
import kitchen.sink.demo.dto.response.TokenResponse;
import kitchen.sink.demo.entity.Member;
import kitchen.sink.demo.entity.OAuthClient;
import kitchen.sink.demo.entity.Session;
import kitchen.sink.demo.exception.AuthenticationFailedException;
import kitchen.sink.demo.exception.InvalidGrantException;
import kitchen.sink.demo.repository.MemberRepository;
import kitchen.sink.demo.repository.OAuthClientRepository;
import kitchen.sink.demo.security.AccessTokenIssuer;
import kitchen.sink.demo.service.SessionService;
import kitchen.sink.demo.util.TokenHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * {@code refresh_token}: swaps a refresh token for new tokens. It checks the client and the account, has
 * {@link SessionService} rotate the refresh token hash, and creates the new access and refresh tokens itself.
 *
 */
@Service
public class RefreshTokenGrantHandler implements GrantHandler {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenGrantHandler.class);

    public static final String GRANT_TYPE = "refresh_token";

    private final SessionService sessionService;
    private final MemberRepository memberRepository;
    private final OAuthClientRepository clients;
    private final AccessTokenIssuer accessTokens;
    private final Clock clock;

    public RefreshTokenGrantHandler(SessionService sessionService, MemberRepository memberRepository,
                                    OAuthClientRepository clients, AccessTokenIssuer accessTokens, Clock clock) {
        this.sessionService = sessionService;
        this.memberRepository = memberRepository;
        this.clients = clients;
        this.accessTokens = accessTokens;
        this.clock = clock;
    }

    @Override
    public String grantType() {
        return GRANT_TYPE;
    }

    @Override
    public void validateMandatoryParams(TokenRequest request) {
        if(request.refreshToken()==null || request.refreshToken().isBlank()){
            throw new InvalidGrantException(Constant.BAD_GRANT);
        }
    }

    /** Refreshes the tokens; any refusal becomes {@code invalid_grant} (400). */
    @Override
    @Transactional
    public TokenResponse handle(TokenRequest request, String ipAddress) {
        log.info("refresh token request received");
        OAuthClient client = clients.findByClientId(request.clientId())
                .filter(OAuthClient::isActive)
                .orElseThrow(() -> {
                    log.warn("refresh refused, client {} is unknown or inactive", request.clientId());
                    return new InvalidGrantException(Constant.BAD_GRANT);
                });

        String newSecret = TokenHasher.newSecret();
        Session session = sessionService.rotateRefreshToken(client.getClientId(),
                TokenHasher.hash(request.refreshToken()), TokenHasher.hash(newSecret));
        // the token carries the email, which is not on the session; the account may be gone by now
        Member owner = memberRepository.findById(session.getUserId())
                .orElseThrow(() -> {
                    log.error("refresh refused, user {} of session {} is gone",
                            session.getUserId(), session.getId());
                    return new AuthenticationFailedException(Constant.BAD_GRANT);
                });
        log.info("refresh token rotated for session {}", session.getId());
        return issueTokens(owner, session, client, newSecret);
    }



    /** Creates the access token (aud: the client's aud list, azp: its client id) and the new refresh token of the session. */
    private TokenResponse issueTokens(Member owner, Session session, OAuthClient client, String refreshSecret) {
        Instant now = clock.instant();
        // refreshing does not extend the session, so the access token is capped at the session's own expiry
        String accessToken = accessTokens.issueAccessToken(owner.getEmail(), session.getId(), client.getAud(),
                client.getClientId(), session.getIpAddress(), now, session.getExpiresAt());
        return new TokenResponse(accessToken, "Bearer",
                accessTokens.accessTokenTtlSeconds(now, session.getExpiresAt()), refreshSecret, session.getId());
    }


}
