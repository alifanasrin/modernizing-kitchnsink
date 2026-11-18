package kitchen.sink.demo.config;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

class MongoConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MongoConfig.class)
            .withBean(MongoDatabaseFactory.class, () -> Mockito.mock(MongoDatabaseFactory.class))
            .withBean(ObservationRegistry.class, ObservationRegistry::create)
            .withPropertyValues("app.mongo.connect-timeout-ms=1000", "app.mongo.server-selection-timeout-ms=1000",
                    "app.mongo.socket-timeout-ms=1000", "app.mongo.max-pool-size=5",
                    "app.mongo.wait-queue-timeout-ms=1000");

    @Test
    @Tag("critical")
    void transactionManagerIsCreatedByDefault_forProductionReplicaSets() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(MongoTransactionManager.class));
    }

    @Test
    void transactionManagerCanBeSwitchedOff_forAStandaloneLocalMongo() {
        runner.withPropertyValues("app.mongo.transactions.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(MongoTransactionManager.class));
    }
}
