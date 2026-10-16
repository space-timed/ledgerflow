package com.ledgerflow.outbox.controller;

import com.ledgerflow.outbox.domain.OutboxEvent;
import com.ledgerflow.outbox.repository.OutboxEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Events & Outbox", description = "Transactional outbox monitor and Kafka event delivery status")
public class EventController {

    private final OutboxEventRepository outboxEventRepository;

    public EventController(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    public record OutboxEventResponse(
            UUID id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String status,
            int retryCount,
            String lastError,
            Instant createdAt,
            Instant publishedAt
    ) {}

    public record OutboxStatsResponse(
            long pendingEventsCount,
            long publishedEventsCount,
            long failedEventsCount,
            long totalEventsCount
    ) {}

    @GetMapping("/outbox")
    @Operation(summary = "List outbox events", description = "Query events in the transactional outbox by status (e.g. PENDING, PUBLISHED, FAILED).")
    public ResponseEntity<List<OutboxEventResponse>> listOutboxEvents(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<OutboxEventResponse> events = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(status.toUpperCase(), PageRequest.of(page, size))
                .stream()
                .map(e -> new OutboxEventResponse(
                        e.getId(),
                        e.getAggregateType(),
                        e.getAggregateId(),
                        e.getEventType(),
                        e.getStatus(),
                        e.getRetryCount(),
                        e.getLastError(),
                        e.getCreatedAt(),
                        e.getPublishedAt()
                ))
                .toList();

        return ResponseEntity.ok(events);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get outbox event queue statistics")
    public ResponseEntity<OutboxStatsResponse> getStats() {
        long pending = outboxEventRepository.countByStatus("PENDING");
        long published = outboxEventRepository.countByStatus("PUBLISHED");
        long failed = outboxEventRepository.countByStatus("FAILED");
        long total = outboxEventRepository.count();

        return ResponseEntity.ok(new OutboxStatsResponse(pending, published, failed, total));
    }
}
