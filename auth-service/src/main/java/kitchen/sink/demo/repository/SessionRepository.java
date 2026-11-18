package kitchen.sink.demo.repository;

import kitchen.sink.demo.entity.Session;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SessionRepository extends MongoRepository<Session, String>, SessionRepositoryCustom {
}
