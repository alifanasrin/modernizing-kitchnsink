package kitchen.sink.demo.dto.request;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
       @NotBlank(message = "First name is mandatory") @Size(min = 1, max = 25)
       @Pattern(regexp = "^[^0-9]*$", message = "First name must not contain digits")
       String firstName,
       @NotBlank(message = "Last name is mandatory") @Size(min = 1, max = 25)
       @Pattern(regexp = "^[^0-9]*$", message = "Last name must not contain digits")
       String lastName,
       @NotBlank(message = "Email is mandatory")@Email(message = "Invalid format of email") String email,
       @NotBlank(message = "Phone number is mandatory")
       @Size(min = 10, max = 12, message = "Size of phone no must be between 10 to 12")
       @Pattern(regexp = "^[0-9]*$", message = "phone no must only be digit")
        String phoneNo,
       // Password guidelines of NIST SP 800-63B / OWASP: any characters (spaces, Unicode, symbols), no trimming or
       // truncating, no normalization. The same bounds (15 to 64) are checked here in UTF-16 units; the exact rule is applied
       // by PasswordPolicy, which counts code points.
       @NotNull(message = "Password is mandatory")
       @Size(min = 15, max = 64, message = "Password must be 15 to 64 characters")
       String password

){
    /** Describes the request with the password hidden. */
    @Override
    public String toString() {
        return "RegistrationRequest[firstName=" + firstName + ", lastName=" + lastName + ", phoneNo=" + phoneNo + ", email=" + email + ", password=***]";
    }
}
