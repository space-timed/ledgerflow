package com.ledgerflow.outbox.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public CompletableFuture<String> publish(String topic, String key, String payload) {
        return kafkaTemplate.send(topic, key, payload)
                .thenApply(result -> result.getRecordMetadata().toString());
    }
}
