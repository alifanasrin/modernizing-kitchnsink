package kitchen.sink.demo.service;

import kitchen.sink.demo.dto.request.LoginRequest;
import kitchen.sink.demo.dto.response.AuthorizationCodeResponse;

/** Proving who the caller is: the password. Sessions are {@link SessionService}'s job. */
public interface AuthService {

    /** Checks the request and the password and issues the authorization code; tokens come only from the token exchange. */
    AuthorizationCodeResponse login(LoginRequest request, String ipAddress);

    /** Revokes the session whose id is in the given access token. */
    void logout(String accessToken);
}
