package kitchen.sink.demo.repository;

import kitchen.sink.demo.entity.OAuthClient;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OAuthClientRepository extends MongoRepository<OAuthClient, String> {

    /** Finds a registered client by its public id. */
    Optional<OAuthClient> findByClientId(String clientId);
}
