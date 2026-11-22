package kitchen.sink.member.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The member auth-service registered. {@code toString} leaves the data out (PII). */
public record CreateMemberRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 10, max = 12) @Pattern(regexp = "^[0-9]*$", message = "must only be digits") String phoneNo,
        @NotBlank @Size(max = 25) String firstName,
        @Size(max = 25) String lastName) {

    @Override
    public String toString() {
        return "CreateMemberRequest[]";
    }
}
