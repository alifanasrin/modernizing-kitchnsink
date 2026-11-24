package kitchen.sink.member.repository;

import kitchen.sink.member.entity.Member;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Needs a real MongoDB at localhost:27017 (collation is applied by the server). */
@Tag("integration")
@SpringBootTest(properties = "spring.mongodb.database=member-collation-test")
@TestPropertySource(locations = "classpath:jwt-test.properties")
class MemberRepositoryTest {

    @Autowired MemberRepository repository;

    @Test
    void shouldSortByLastThenFirstNameIgnoringCase() {
        repository.deleteAll();
        repository.saveAll(List.of(
                new Member(null, "a@x.com", "1234567890", "Aleya", "Begum"),
                new Member(null, "b@x.com", "1234567890", "Prajwal", "K"),
                new Member(null, "c@x.com", "1234567890", "Nabila", "Reza"),
                new Member(null, "d@x.com", "1234567890", "Nabil", "Reza"),
                new Member(null, "e@x.com", "1234567890", "sahana", "khan"),
                new Member(null, "f@x.com", "1234567890", "tejal", "kumari"),
                new Member(null, "g@x.com", "1234567890", "Dena", "Winchester")));

        List<String> firstNames = repository.findAllByOrderByLastNameAscFirstNameAsc(PageRequest.of(0, 10))
                .map(Member::firstName).getContent();

        repository.deleteAll();
        assertThat(firstNames).containsExactly("Aleya", "Prajwal", "sahana", "tejal", "Nabil", "Nabila", "Dena");
    }
}
