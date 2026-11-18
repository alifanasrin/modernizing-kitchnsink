package kitchen.sink.demo.entity;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "member")
@AllArgsConstructor
@Getter
@NoArgsConstructor
public class Member {
    @Id
    private String id;

    @Field(name = "email")
    @NotBlank(message = "email is required")
    private String email;

    @Field(name = "phone_number")
    @NotBlank(message = "phone no is required")
    @Size(min = 10, max = 12, message = "Size of phone no must be between 10 to 12")
    @Pattern(regexp = "^[0-9]*$", message = "phone no must only be digit")
    private String phoneNo;

    @NotBlank(message = "First name is required")
    @Size(max = 25, message = "First name must not exceed 25 characters")
    @Field(name = "first_name")
    private String firstName;

    /** Null on members registered with a single-word name before the name was split. */
    @Size(max = 25, message = "Last name must not exceed 25 characters")
    @Field(name = "last_name")
    private String lastName;

    /** BCrypt hash; never the raw password. Null for members registered before passwords existed. */
    @Field(name = "password_hash")
    private String passwordHash;

    public Member(String email, String phoneNumber, String firstName, String lastName) {
        this(email, phoneNumber, firstName, lastName, null);
    }

    public Member(String email, String phoneNumber, String firstName, String lastName, String passwordHash) {
        this.email = email;
        this.phoneNo = phoneNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.passwordHash = passwordHash;
    }
}
