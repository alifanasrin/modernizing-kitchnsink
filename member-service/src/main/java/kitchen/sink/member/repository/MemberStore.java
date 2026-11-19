package kitchen.sink.member.repository;

import kitchen.sink.member.entity.Member;

/**
 * The only write path to members, kept apart from the read-only {@link MemberRepository} so the API side can never
 * save or delete. Used by the Kafka consumer only.
 */
public interface MemberStore {

    /**
     * Inserts the member unless one with the same email exists (idempotent: a redelivered event changes nothing).
     *
     * @return true if the member was created, false if it already existed
     */
    boolean createIfAbsent(Member member);
}
