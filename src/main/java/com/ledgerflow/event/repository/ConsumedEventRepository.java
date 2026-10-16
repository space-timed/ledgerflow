package com.ledgerflow.event.repository;

import com.ledgerflow.event.domain.ConsumedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ConsumedEventRepository extends JpaRepository<ConsumedEvent, ConsumedEvent.ConsumedEventId> {

    default boolean hasBeenConsumed(UUID eventId, String consumerGroup) {
        return existsById(new ConsumedEvent.ConsumedEventId(eventId, consumerGroup));
    }

    default void markConsumed(UUID eventId, String consumerGroup) {
        save(new ConsumedEvent(eventId, consumerGroup));
    }
}
