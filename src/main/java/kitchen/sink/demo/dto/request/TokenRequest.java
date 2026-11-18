package kitchen.sink.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The JSON body of the token endpoint. Which of {@code code, codeVerifier} (authorization_code) or
 * {@code refreshToken} (refresh_token) are needed depends on the grant type; its handler checks them.
 */
public record TokenRequest(
        @NotBlank(message = "grantType is mandatory") @Size(max = 32) String grantType,
        @NotBlank(message = "clientId is mandatory") @Size(max = 128) String clientId,
        @Size(max = 128) String code,
        @Pattern(regexp = "[A-Za-z0-9\\-._~]{43,128}", message = "codeVerifier must be 43-128 unreserved characters")
        String codeVerifier,
        @Size(max = 256) String refreshToken) {
    /** Describes the request with the secrets hidden. */
    @Override
    public String toString() {
        return "TokenRequest[grantType=" + grantType + ", clientId=" + clientId + ", secrets=***]";
    }
}
