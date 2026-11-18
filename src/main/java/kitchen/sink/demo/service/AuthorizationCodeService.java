package kitchen.sink.demo.service;

import kitchen.sink.demo.dto.request.AuthorizationRequest;
import kitchen.sink.demo.dto.response.AuthorizationCodeResponse;

/**
 * The authorization-server side of the authorization-code flow with PKCE: accepts the authorization request, keeps it
 * until the login, and issues the code.
 */
public interface AuthorizationCodeService {

    /**
     * Handles {@code GET /oauth2/authorize}. Returns the URL to redirect the browser to: the login page with a
     * {@code request_id}, or the client's redirect URI with an {@code error}. An unknown client or unregistered
     * redirect URI is never redirected to; it throws instead (RFC 6749 section 4.1.2.1).
     */
    String authorize(AuthorizationRequest request);

    /** Checks that the login belongs to an open (known, unexpired, not yet issued) authorization request. */
    void validate(String requestId);

    /**
     * Issues the one-time code for the open request, now that this member passed the password check (the request
     * learns the member only here), and builds the redirect to the client.
     */
    AuthorizationCodeResponse complete(String requestId, String userId);
}
