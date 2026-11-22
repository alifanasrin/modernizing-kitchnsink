package kitchen.sink.demo.dto.request;

/** Body of member-service's {@code POST /members}. No credential; {@code toString} hides the PII. */
public record CreateMemberRequest(String email, String phoneNo, String firstName, String lastName) {
    @Override
    public String toString() {
        return "CreateMemberRequest[]";
    }
}
