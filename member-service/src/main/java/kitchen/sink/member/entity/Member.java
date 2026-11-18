package kitchen.sink.member.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A member as stored by auth-service (the {@code member} collection of the shared database). Read-only here: it has
 * no password hash on purpose, so a credential can never leak through this service.
 */
@Document(collection = "member")
public record Member(
        @Id String id,
        String email,
        @Field("phone_number") String phoneNo,
        @Field("first_name") String firstName,
        @Field("last_name") String lastName) {
}
