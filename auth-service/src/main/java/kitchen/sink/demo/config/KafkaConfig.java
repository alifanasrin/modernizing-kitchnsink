package kitchen.sink.demo.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    /** The producer makes sure the topic exists; member-service declares the same topic, which is harmless. */
    @Bean
    NewTopic memberRegisteredTopic(@Value("${app.kafka.topic.member-registered}") String name,
                                   @Value("${app.kafka.topic.partitions}") int partitions,
                                   @Value("${app.kafka.topic.replicas}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
}
