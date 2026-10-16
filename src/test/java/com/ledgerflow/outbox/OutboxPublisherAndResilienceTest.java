package com.ledgerflow.outbox;

import com.ledgerflow.common.config.KafkaConfig;
import com.ledgerflow.outbox.domain.OutboxEvent;
import com.ledgerflow.outbox.repository.OutboxEventRepository;
import com.ledgerflow.outbox.service.EventPublisher;
import com.ledgerflow.outbox.service.OutboxPublisher;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherAndResilienceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private EventPublisher eventPublisher;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(outboxEventRepository, eventPublisher);
        ReflectionTestUtils.setField(outboxPublisher, "batchSize", 10);
        ReflectionTestUtils.setField(outboxPublisher, "maxRetries", 3);
        ReflectionTestUtils.setField(outboxPublisher, "publisherEnabled", true);
    }

    @Test
    @DisplayName("OutboxPublisher successfully sends pending events to Kafka and updates status to PUBLISHED")
    void testSuccessfulPublishing() {
        OutboxEvent event = new OutboxEvent("PAYMENT", "pay_001", "payment.captured", "{\"amount\":5000}");
        when(outboxEventRepository.findPendingEventsForUpdate(10)).thenReturn(List.of(event));

        when(eventPublisher.publish(eq(KafkaConfig.PAYMENT_EVENTS_TOPIC), eq("pay_001"), anyString()))
                .thenReturn(CompletableFuture.completedFuture("ack"));

        outboxPublisher.publishPendingEvents();

        assertEquals("PUBLISHED", event.getStatus());
        assertNotNull(event.getPublishedAt());
        verify(outboxEventRepository, times(1)).save(event);
    }

    @Test
    @DisplayName("When Kafka broker is unreachable, OutboxPublisher records error, increments retry count, and preserves PENDING status")
    void testKafkaOutageRecovery() {
        OutboxEvent event = new OutboxEvent("PAYMENT", "pay_002", "payment.captured", "{\"amount\":10000}");
        when(outboxEventRepository.findPendingEventsForUpdate(10)).thenReturn(List.of(event));

        CompletableFuture<String> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Broker connection refused"));

        when(eventPublisher.publish(anyString(), anyString(), anyString())).thenAnswer(inv -> failedFuture);

        outboxPublisher.publishPendingEvents();

        assertEquals("PENDING", event.getStatus(), "Status must remain PENDING for subsequent retry");
        assertEquals(1, event.getRetryCount());
        assertNotNull(event.getLastError());
        assertTrue(event.getLastError().contains("Broker connection refused"));
        verify(outboxEventRepository, times(1)).save(event);
    }

    @Test
    @DisplayName("When max retries exceeded, event transitions to FAILED for operational inspection")
    void testMaxRetriesExceededTransitionsToFailed() {
        OutboxEvent event = new OutboxEvent("PAYMENT", "pay_003", "payment.captured", "{\"amount\":2000}");
        event.setRetryCount(2); // max is 3

        when(outboxEventRepository.findPendingEventsForUpdate(10)).thenReturn(List.of(event));

        CompletableFuture<String> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Broker timeout"));

        when(eventPublisher.publish(anyString(), anyString(), anyString())).thenAnswer(inv -> failedFuture);

        outboxPublisher.publishPendingEvents();

        assertEquals("FAILED", event.getStatus(), "Status must transition to FAILED when retry limit reached");
        assertEquals(3, event.getRetryCount());
        verify(outboxEventRepository, times(1)).save(event);
    }
}
