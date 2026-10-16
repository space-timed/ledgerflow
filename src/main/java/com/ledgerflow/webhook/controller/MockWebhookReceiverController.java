package com.ledgerflow.webhook.controller;

import com.ledgerflow.webhook.service.WebhookSigner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@RestController
@RequestMapping("/api/v1/mock/webhook-receiver")
@Tag(name = "Mock Webhook Receiver", description = "Local test receiver endpoint for validating signed webhook delivery")
public class MockWebhookReceiverController {

    private static final Logger log = LoggerFactory.getLogger(MockWebhookReceiverController.class);
    private final ConcurrentLinkedDeque<ReceivedWebhook> receivedDeliveries = new ConcurrentLinkedDeque<>();

    public record ReceivedWebhook(
            Instant receivedAt,
            String signatureHeader,
            String payload
    ) {}

    public record ReceiverResponse(
            String status,
            Instant receivedAt,
            int totalReceivedCount
    ) {}

    @PostMapping
    @Operation(summary = "Receive signed webhook event (Mock Ingress)", description = "Accepts HTTP POST callbacks, records signature headers, and verifies delivery.")
    public ResponseEntity<ReceiverResponse> receiveWebhook(
            @RequestHeader(value = WebhookSigner.SIGNATURE_HEADER, required = false) String signatureHeader,
            @RequestBody(required = false) String payload) {

        log.info("Mock Webhook Receiver received event! Signature Header: [{}], Payload: [{}]",
                signatureHeader, payload);

        receivedDeliveries.addFirst(new ReceivedWebhook(Instant.now(), signatureHeader, payload));
        if (receivedDeliveries.size() > 50) {
            receivedDeliveries.removeLast();
        }

        return ResponseEntity.ok(new ReceiverResponse("RECEIVED", Instant.now(), receivedDeliveries.size()));
    }

    @GetMapping("/deliveries")
    @Operation(summary = "Inspect all captured mock webhook deliveries")
    public ResponseEntity<List<ReceivedWebhook>> getDeliveries() {
        return ResponseEntity.ok(new ArrayList<>(receivedDeliveries));
    }

    @DeleteMapping("/deliveries")
    @Operation(summary = "Clear mock webhook delivery buffer")
    public ResponseEntity<Void> clearDeliveries() {
        receivedDeliveries.clear();
        return ResponseEntity.noContent().build();
    }
}
