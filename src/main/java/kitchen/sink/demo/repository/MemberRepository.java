package kitchen.sink.demo.repository;

import kitchen.sink.demo.entity.Member;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends MongoRepository<Member, String> {

    /** Finds a member by (normalized) email address. */
    Optional<Member> findByEmail(String email);
}
