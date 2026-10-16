package com.ledgerflow.outbox.service;

import java.util.concurrent.CompletableFuture;

public interface EventPublisher {
    CompletableFuture<String> publish(String topic, String key, String payload);
}
