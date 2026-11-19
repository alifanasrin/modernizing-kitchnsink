package kitchen.sink.member.repository;

import kitchen.sink.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/** Read-only access to members: extends the marker {@link Repository}, so there is no save or delete method. */
@org.springframework.stereotype.Repository
public interface MemberRepository extends Repository<Member, String> {

    /** Returns a page of members sorted by last name, then first name. */
    Page<Member> findAllByOrderByLastNameAscFirstNameAsc(Pageable pageable);

    /** Finds a member by (normalized) email address. */
    Optional<Member> findByEmail(String email);
}
