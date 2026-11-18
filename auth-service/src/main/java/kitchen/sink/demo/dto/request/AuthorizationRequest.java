package kitchen.sink.demo.dto.request;

/**
 * The query parameters of {@code GET /oauth2/authorize} (RFC 6749 section 4.1.1 with PKCE, RFC 7636). All are
 * nullable here (the optional {@code state} is an opaque client value echoed back unchanged)  because the service decides how each missing or wrong one is reported.
 */
public record AuthorizationRequest(String responseType, String clientId, String redirectUri, String codeChallenge,
                                   String codeChallengeMethod, String state) {

    /** A request without {@code state}. */
    public AuthorizationRequest(String responseType, String clientId, String redirectUri, String codeChallenge,
                                String codeChallengeMethod) {
        this(responseType, clientId, redirectUri, codeChallenge, codeChallengeMethod, null);
    }
}
