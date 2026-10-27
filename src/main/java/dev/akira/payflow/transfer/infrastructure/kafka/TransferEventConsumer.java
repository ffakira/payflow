package dev.akira.payflow.transfer.infrastructure.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransferEventConsumer {

    public static final Logger log = LoggerFactory.getLogger(TransferEventConsumer.class);

    @KafkaListener(topics = TransferKafkaConfig.TRANSFER_COMPLETED_TOPIC)
    public void consume(String message) {
        log.info("Receive Kafka message: {}", message);
    }
}
