package dev.akira.payflow.transfer.adapter.out.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransferEventPublisher {

    public static final String TOPIC = "transfer-completed";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public TransferEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String transactionId) {
        kafkaTemplate.send(
                TOPIC,
                transactionId,
                "Transfer completed: " + transactionId
        );
    }
}
