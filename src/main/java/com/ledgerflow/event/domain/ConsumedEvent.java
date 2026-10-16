package com.ledgerflow.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "consumed_events")
public class ConsumedEvent {

    @EmbeddedId
    private ConsumedEventId id;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    public ConsumedEvent() {
    }

    public ConsumedEvent(UUID eventId, String consumerGroup) {
        this.id = new ConsumedEventId(eventId, consumerGroup);
        this.processedAt = Instant.now();
    }

    public ConsumedEventId getId() {
        return id;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    @Embeddable
    public static class ConsumedEventId implements Serializable {
        @Column(name = "event_id", nullable = false)
        private UUID eventId;

        @Column(name = "consumer_group", nullable = false, length = 100)
        private String consumerGroup;

        public ConsumedEventId() {
        }

        public ConsumedEventId(UUID eventId, String consumerGroup) {
            this.eventId = eventId;
            this.consumerGroup = consumerGroup;
        }

        public UUID getEventId() {
            return eventId;
        }

        public String getConsumerGroup() {
            return consumerGroup;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConsumedEventId that = (ConsumedEventId) o;
            return Objects.equals(eventId, that.eventId) && Objects.equals(consumerGroup, that.consumerGroup);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId, consumerGroup);
        }
    }
}
