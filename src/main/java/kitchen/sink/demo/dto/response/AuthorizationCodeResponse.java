package kitchen.sink.demo.dto.response;

/**
 * Returned once the password is accepted. {@code redirectTo} is the client's registered redirect URI with the
 * one-time authorization code as its {@code code} query parameter; the login page sends the browser there
 * (RFC 6749 section 4.1.2). No tokens are issued here. {@code expiresIn} is the code lifetime in seconds.
 */
public record AuthorizationCodeResponse(String redirectTo, long expiresIn) {
    /** Describes the response with the code hidden. */
    @Override
    public String toString() {
        return "AuthorizationCodeResponse[redirectTo=***]";
    }
}
