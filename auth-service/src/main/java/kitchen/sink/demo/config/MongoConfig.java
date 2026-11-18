package kitchen.sink.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.ReadConcern;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.observability.ObservabilitySettings;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;

import java.util.concurrent.TimeUnit;

/**
 * All MongoDB wiring in one place: client timeouts and tracing, the document mapping, and transactions.
 * This class injects nothing itself (only its bean methods take parameters), so the MongoClient can still be built
 * from the customizers below without a circular dependency.
 */
@Configuration
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    /** Applies the configured connect, read, pool and selection timeouts to the Mongo client. */
    @Bean
    MongoClientSettingsBuilderCustomizer mongoTimeoutsCustomizer(
            @Value("${app.mongo.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${app.mongo.server-selection-timeout-ms}") long serverSelectionTimeoutMs,
            @Value("${app.mongo.socket-timeout-ms}") int socketTimeoutMs,
            @Value("${app.mongo.max-pool-size}") int maxPoolSize,
            @Value("${app.mongo.wait-queue-timeout-ms}") long waitQueueTimeoutMs) {
        return builder -> builder
                .applyToSocketSettings(s -> s
                        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                        .readTimeout(socketTimeoutMs, TimeUnit.MILLISECONDS))
                .applyToClusterSettings(c -> c
                        .serverSelectionTimeout(serverSelectionTimeoutMs, TimeUnit.MILLISECONDS))
                .applyToConnectionPoolSettings(p -> p
                        .maxSize(maxPoolSize)
                        .maxWaitTime(waitQueueTimeoutMs, TimeUnit.MILLISECONDS));
    }


    /** Stops Mongo documents from storing the _class type field. */
    @Bean
    static BeanPostProcessor removeClassFieldPostProcessor() {
        return new BeanPostProcessor() {
            /** Sets a type mapper without _class on the Mongo converter bean. */
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof MappingMongoConverter converter) {
                    converter.setTypeMapper(new DefaultMongoTypeMapper(null));
                }
                return bean;
            }
        };
    }

    /**
     * Turns on {@code @Transactional} for MongoDB, with majority read/write concerns and a commit time limit.
     * MongoDB only supports multi-document transactions on a replica set (or sharded cluster), so production must
     * run one. A standalone server (typical for local development) rejects every transaction: set
     * {@code MONGO_TRANSACTIONS_ENABLED=false} there and {@code @Transactional} is then ignored.
     */
    @Bean
    @ConditionalOnProperty(name = "app.mongo.transactions.enabled", havingValue = "true", matchIfMissing = true)
    MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory,
            @Value("${app.mongo.transactions.max-commit-time-ms:5000}") long maxCommitTimeMs) {
        log.info("mongo transactions enabled (majority concerns, commit limit {} ms)", maxCommitTimeMs);
        TransactionOptions options = TransactionOptions.builder()
                .readConcern(ReadConcern.MAJORITY)
                .writeConcern(WriteConcern.MAJORITY)
                .maxCommitTime(maxCommitTimeMs, TimeUnit.MILLISECONDS)
                .build();
        return new MongoTransactionManager(databaseFactory, options);
    }
}
