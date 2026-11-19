package kitchen.sink.member.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A member of this service's own {@code member} collection, created from auth-service's member-registered events.
 * It has no password hash on purpose, so a credential can never reach this service or leak through it.
 */
@Document(collection = "member")
public record Member(
        @Id String id,
        @Indexed(name = "uk_member_email", unique = true) String email,
        @Field("phone_number") String phoneNo,
        @Field("first_name") String firstName,
        @Field("last_name") String lastName) {
}
