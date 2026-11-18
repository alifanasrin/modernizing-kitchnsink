package kitchen.sink.demo.service;

import kitchen.sink.demo.dto.request.TokenRequest;
import kitchen.sink.demo.dto.response.TokenResponse;

/** The second half of the flow: swaps an authorization code plus PKCE verifier for the tokens. */
public interface TokenExchangeService {

    /** Redeems the code (once) and starts a session. */
    TokenResponse exchange(TokenRequest request, String ipAddress);
}
