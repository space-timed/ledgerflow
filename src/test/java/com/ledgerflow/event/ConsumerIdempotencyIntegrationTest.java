package com.ledgerflow.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.event.consumer.PaymentEventListener;
import com.ledgerflow.event.repository.ConsumedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConsumerIdempotencyIntegrationTest {

    @Autowired
    private PaymentEventListener paymentEventListener;

    @Autowired
    private ConsumedEventRepository consumedEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Consumer Idempotency: Duplicate Kafka deliveries with same event ID are safely deduplicated")
    void testConsumerDeduplication() throws Exception {
        UUID eventId = UUID.randomUUID();
        String message = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId.toString(),
                "eventType", "payment.captured",
                "paymentId", "pay_test_99",
                "amountMinorUnits", 5000,
                "currency", "INR"
        ));

        // 1. Deliver event for the first time
        paymentEventListener.onPaymentEvent(message, "pay_test_99", 0, 100L);

        // Verify marked as consumed in database
        assertTrue(consumedEventRepository.hasBeenConsumed(eventId, PaymentEventListener.CONSUMER_GROUP));

        // 2. Deliver identical event a second time (simulating at-least-once Kafka duplicate delivery)
        assertDoesNotThrow(() ->
                paymentEventListener.onPaymentEvent(message, "pay_test_99", 0, 101L)
        );

        // Ensure still cleanly registered without duplicate errors or double actions
        assertTrue(consumedEventRepository.hasBeenConsumed(eventId, PaymentEventListener.CONSUMER_GROUP));
    }
}
