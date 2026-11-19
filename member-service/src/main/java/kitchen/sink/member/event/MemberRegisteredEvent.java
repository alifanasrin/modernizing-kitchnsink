package kitchen.sink.member.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The member-registered message published by auth-service. Unknown fields are ignored so an additive
 * change does not break this consumer. {@code toString} leaves the member's data out (PII).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MemberRegisteredEvent(
        @NotNull @Valid Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            @NotBlank String memberId,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 10, max = 12) @Pattern(regexp = "^[0-9]*$", message = "must only be digits") String phoneNo,
            @NotBlank @Size(max = 25) String firstName,
            @Size(max = 25) String lastName) {
        @Override
        public String toString() {
            return "Data[memberId=" + memberId + "]";
        }
    }

    @Override
    public String toString() {
        return "MemberRegisteredEvent[memberId=" + (data == null ? null : data.memberId()) + "]";
    }
}
