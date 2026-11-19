package kitchen.sink.member.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * The member-registered message published by auth-service (contract v1.x). Unknown fields are ignored so an additive
 * minor version does not break this consumer. {@code toString} leaves the member's data out (PII).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MemberRegisteredEvent(
        @NotBlank String eventId,
        @NotBlank String eventType,
        @NotBlank String schemaVersion,
        String source,
        Instant occurredAt,
        @NotNull @Valid Data data) {

    public static final String EVENT_TYPE = "MEMBER_REGISTERED";
    public static final String SUPPORTED_MAJOR_VERSION = "1";

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
        return "MemberRegisteredEvent[eventId=" + eventId + ", eventType=" + eventType + "]";
    }
}
