package dev.akira.payflow.transfer.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class TransferKafkaConfig {

    public static final String TRANSFER_COMPLETED_TOPIC = "transfer-completed";

    @Bean
    NewTopic transferCompletedTopic() {
        return TopicBuilder
                .name(TRANSFER_COMPLETED_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
