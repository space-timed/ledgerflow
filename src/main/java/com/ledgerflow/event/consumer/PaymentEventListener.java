package com.ledgerflow.event.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.common.config.KafkaConfig;
import com.ledgerflow.event.repository.ConsumedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);
    public static final String CONSUMER_GROUP = "payment-engine-core-group";

    private final ConsumedEventRepository consumedEventRepository;
    private final ObjectMapper objectMapper;

    public PaymentEventListener(ConsumedEventRepository consumedEventRepository,
                                ObjectMapper objectMapper) {
        this.consumedEventRepository = consumedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = KafkaConfig.PAYMENT_EVENTS_TOPIC,
            groupId = CONSUMER_GROUP,
            autoStartup = "${spring.kafka.listener.auto-startup:true}"
    )
    @Transactional
    public void onPaymentEvent(@Payload String message,
                               @Header(KafkaHeaders.RECEIVED_KEY) String key,
                               @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                               @Header(KafkaHeaders.OFFSET) long offset) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventIdStr = root.has("eventId") ? root.get("eventId").asText() : null;

            if (eventIdStr == null) {
                log.warn("Received event without eventId on partition [{}] offset [{}]: {}", partition, offset, message);
                return;
            }

            UUID eventId = UUID.fromString(eventIdStr);

            // Consumer Idempotency Check
            if (consumedEventRepository.hasBeenConsumed(eventId, CONSUMER_GROUP)) {
                log.info("DUPLICATE EVENT DETECTED: Discarding event [{}] already processed by consumer group [{}].",
                        eventId, CONSUMER_GROUP);
                return;
            }

            // Record consumption atomically
            consumedEventRepository.markConsumed(eventId, CONSUMER_GROUP);

            String eventType = root.has("eventType") ? root.get("eventType").asText() : "unknown";
            log.info("Successfully ingested domain event [{}] type [{}] for aggregate [{}] at offset [{}]",
                    eventId, eventType, key, offset);

        } catch (Exception e) {
            log.error("Failed to process payment event from partition [{}] offset [{}]: {}", partition, offset, e.getMessage(), e);
            throw new RuntimeException("Error processing Kafka message, triggering retry/DLT", e);
        }
    }
}
