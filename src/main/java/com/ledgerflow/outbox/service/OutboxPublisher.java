package com.ledgerflow.outbox.service;

import com.ledgerflow.common.config.KafkaConfig;
import com.ledgerflow.outbox.domain.OutboxEvent;
import com.ledgerflow.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final EventPublisher eventPublisher;

    @Value("${ledgerflow.outbox.batch-size:50}")
    private int batchSize = 50;

    @Value("${ledgerflow.outbox.max-retries:10}")
    private int maxRetries = 10;

    @Value("${ledgerflow.outbox.publisher-enabled:true}")
    private boolean publisherEnabled = true;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           EventPublisher eventPublisher) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${ledgerflow.outbox.polling-rate-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        if (!publisherEnabled) {
            return;
        }

        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEventsForUpdate(batchSize);
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found [{}] pending outbox events for publishing", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // Publish to Kafka with partition key = aggregateId (guarantees partition order per payment)
                eventPublisher.publish(
                        KafkaConfig.PAYMENT_EVENTS_TOPIC,
                        event.getAggregateId(),
                        event.getPayload()
                ).get(3, TimeUnit.SECONDS);

                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);

                log.debug("Published outbox event [{}] type [{}] to topic [{}]",
                        event.getId(), event.getEventType(), KafkaConfig.PAYMENT_EVENTS_TOPIC);
            } catch (Exception e) {
                event.setRetryCount(event.getRetryCount() + 1);
                event.setLastError(e.getMessage());

                if (event.getRetryCount() >= maxRetries) {
                    event.setStatus("FAILED");
                    log.error("Outbox event [{}] exceeded max retries [{}]. Marked as FAILED.", event.getId(), maxRetries);
                } else {
                    log.warn("Failed to publish outbox event [{}] (attempt {}/{}): {}",
                            event.getId(), event.getRetryCount(), maxRetries, e.getMessage());
                }
                outboxEventRepository.save(event);
                break; // Stop batch on broker connection failure to avoid cascading timeouts
            }
        }
    }
}
