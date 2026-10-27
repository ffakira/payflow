package dev.akira.payflow.transfer.adapter.in.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransferCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(TransferCompletedListener.class);

    @KafkaListener(topics = "transfer-completed")
    public void onTransferCompleted(String message) {
        log.info("Received Kafka event: {}", message);
    }
}
