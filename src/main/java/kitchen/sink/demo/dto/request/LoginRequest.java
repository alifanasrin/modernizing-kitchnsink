package kitchen.sink.demo.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Credentials entered on the login page. {@code requestId} ties them to the authorization request that
 * {@code GET /oauth2/authorize} stored (client, redirect URI, code challenge), so none of those are sent again.
 */
public record LoginRequest(
        @NotBlank(message = "Email is mandatory") @Email(message = "Invalid format of email") String email,
        @NotNull(message = "Password is mandatory") @Size(min = 15, max = 64, message = "Password must be 15 to 64 characters") String password,
        @NotBlank(message = "requestId is mandatory") @Size(max = 128, message = "requestId too long") String requestId
) {
    /** Describes the request with the password hidden. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***, requestId=" + requestId + "]";
    }
}
