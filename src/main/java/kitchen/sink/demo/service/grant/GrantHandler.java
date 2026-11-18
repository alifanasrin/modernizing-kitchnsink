package kitchen.sink.demo.service.grant;

import kitchen.sink.demo.dto.request.TokenRequest;
import kitchen.sink.demo.dto.response.TokenResponse;

/**
 * One OAuth grant type of the token endpoint. A new grant type is a new implementation of this interface (picked up
 * by {@link GrantHandlerFactory} on its own); nothing else changes.
 */
public interface GrantHandler {

    /** The {@code grant_type} value this handler serves, e.g. {@code authorization_code}. */
    String grantType();

    /**
     * Checks that the parameters this grant needs are present (400 {@code InvalidGrantException} otherwise).
     * Called before {@link #handle}, so a malformed request never touches a code or a token.
     */
    void validateMandatoryParams(TokenRequest request);

    /** Returns the tokens for a request whose mandatory parameters are present, or throws if the grant is refused. */
    TokenResponse handle(TokenRequest request, String ipAddress);
}
