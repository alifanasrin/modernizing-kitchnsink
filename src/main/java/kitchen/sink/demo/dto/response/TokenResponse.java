package kitchen.sink.demo.dto.response;

/** {@code expiresIn} is the access token lifetime in seconds. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn,
                            String refreshToken, String sessionId) {
    /** Describes the response with the tokens hidden. */
    @Override
    public String toString() {
        return "TokenResponse[sessionId=" + sessionId + ", tokens=***]";
    }
}
