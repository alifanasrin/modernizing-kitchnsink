package kitchen.sink.member.repository;

import kitchen.sink.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/** Access to members: reads for the REST API and the insert used by {@code POST /members} (via {@code MemberService}). */
@org.springframework.stereotype.Repository
public interface MemberRepository extends MongoRepository<Member, String> {

    /** Returns a page of members sorted by last name, then first name. */
    Page<Member> findAllByOrderByLastNameAscFirstNameAsc(Pageable pageable);

    /** Finds a member by (normalized) email address. */
    Optional<Member> findByEmail(String email);
}
