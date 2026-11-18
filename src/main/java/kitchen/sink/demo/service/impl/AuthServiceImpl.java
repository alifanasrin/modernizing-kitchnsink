package kitchen.sink.demo.service.impl;

import kitchen.sink.demo.constant.Constant;
import kitchen.sink.demo.dto.request.LoginRequest;
import kitchen.sink.demo.dto.response.AuthorizationCodeResponse;
import kitchen.sink.demo.entity.Member;
import kitchen.sink.demo.exception.AuthenticationFailedException;
import kitchen.sink.demo.security.AccessTokenIssuer;
import kitchen.sink.demo.security.MemberPrincipal;
import kitchen.sink.demo.security.PasswordPolicy;
import kitchen.sink.demo.service.AuthService;
import kitchen.sink.demo.service.AuthorizationCodeService;
import kitchen.sink.demo.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final AuthenticationManager authenticationManager;
    private final PasswordPolicy passwordPolicy;
    private final SessionService sessionService;
    private final AuthorizationCodeService authorizationCodes;
    private final JwtDecoder jwtDecoder;

    public AuthServiceImpl( AuthenticationManager authenticationManager,
                           PasswordPolicy passwordPolicy,
                           SessionService sessionService, AuthorizationCodeService authorizationCodes,
                           JwtDecoder jwtDecoder) {
        this.authenticationManager = authenticationManager;
        this.passwordPolicy = passwordPolicy;
        this.sessionService = sessionService;
        this.authorizationCodes = authorizationCodes;
        this.jwtDecoder = jwtDecoder;
    }

    /** Verifies email and password, then issues the authorization code for the open request (no session yet). */
    @Override
    @Transactional
    public AuthorizationCodeResponse login(LoginRequest request, String ipAddress) {
        log.debug("password login attempt from {}", ipAddress);
        authorizationCodes.validate(request.requestId());
        Member member = authenticate(request.email(), request.password(), ipAddress);
        AuthorizationCodeResponse response = authorizationCodes.complete(request.requestId(), member.getId());
        log.info("password accepted for user {}, authorization code issued", member.getId());
        return response;
    }

    /**
     * Checks the password through the authentication manager and returns the member. Every failure (unknown email, no
     * password, wrong or over-long password) is the same 401, so nothing reveals whether an email is registered.
     */
    private Member authenticate(String email, String password, String ipAddress) {
        // an over-long password never matches; refuse it before it is hashed
        if (passwordPolicy.exceedsMaxLength(password)) {
            log.warn("failed login attempt from {}", ipAddress);
            throw new AuthenticationFailedException(Constant.BAD_CREDENTIALS);
        }
        try {
            Authentication result = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, password));
            return ((MemberPrincipal) result.getPrincipal()).member();
        } catch (AuthenticationException e) {
            log.warn("failed login attempt from {}", ipAddress);
            throw new AuthenticationFailedException(Constant.BAD_CREDENTIALS);
        }
    }

    /** Reads the session id from the access token and revokes that session. */
    @Override
    @Transactional
    public void logout(String accessToken) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(accessToken);
        } catch (JwtException e) {
            log.warn("logout refused, invalid access token: {}", e.getMessage());
            throw new AuthenticationFailedException(Constant.BAD_ACCESS_TOKEN);
        }
        String sessionId = jwt.getClaimAsString(AccessTokenIssuer.SESSION_CLAIM);
        if (sessionId == null) {
            log.warn("logout refused, access token has no session id");
            throw new AuthenticationFailedException(Constant.BAD_ACCESS_TOKEN);
        }
        sessionService.revoke(sessionId);
        log.info("logout: session {} revoked", sessionId);
    }
}
