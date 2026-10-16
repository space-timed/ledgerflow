package com.ledgerflow.webhook.controller;

import com.ledgerflow.common.security.MerchantContext;
import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.webhook.repository.WebhookDeliveryRepository;
import com.ledgerflow.webhook.service.WebhookDispatcherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks", description = "Merchant webhook subscription management and delivery logs")
public class WebhookController {

    private final WebhookDispatcherService dispatcherService;
    private final WebhookDeliveryRepository deliveryRepository;

    public WebhookController(WebhookDispatcherService dispatcherService,
                             WebhookDeliveryRepository deliveryRepository) {
        this.dispatcherService = dispatcherService;
        this.deliveryRepository = deliveryRepository;
    }

    public record CreateSubscriptionRequest(
            @NotBlank(message = "Target URL is required") String targetUrl,
            List<String> events
    ) {}

    @PostMapping("/subscriptions")
    @Operation(summary = "Register a new webhook subscription", description = "Generates a unique HMAC secret key and registers event listener URL.")
    public ResponseEntity<WebhookDispatcherService.SubscriptionResult> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request) {

        Merchant merchant = MerchantContext.getMerchant();
        WebhookDispatcherService.SubscriptionResult result = dispatcherService.createSubscription(
                merchant,
                request.targetUrl(),
                request.events()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/subscriptions")
    @Operation(summary = "List webhook subscriptions for current merchant")
    public ResponseEntity<List<WebhookDispatcherService.SubscriptionResult>> listSubscriptions() {
        Merchant merchant = MerchantContext.getMerchant();
        List<WebhookDispatcherService.SubscriptionResult> results = dispatcherService.listSubscriptions(merchant.getId());
        return ResponseEntity.ok(results);
    }

    @PostMapping("/deliveries/{id}/redeliver")
    @Operation(summary = "Manually trigger immediate redelivery of a webhook event")
    public ResponseEntity<WebhookDispatcherService.DeliveryResult> redeliver(@PathVariable UUID id) {
        Merchant merchant = MerchantContext.getMerchant();
        WebhookDispatcherService.DeliveryResult result = dispatcherService.redeliver(id, merchant.getId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/deliveries")
    @Operation(summary = "List recent webhook delivery attempts")
    public ResponseEntity<List<WebhookDispatcherService.DeliveryResult>> listDeliveries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Merchant merchant = MerchantContext.getMerchant();
        List<WebhookDispatcherService.DeliveryResult> results = deliveryRepository
                .findByMerchantIdOrderByCreatedAtDesc(merchant.getId(), PageRequest.of(page, size))
                .stream()
                .map(d -> new WebhookDispatcherService.DeliveryResult(
                        d.getId(),
                        d.getEventId(),
                        d.getEventType(),
                        d.getSubscription().getTargetUrl(),
                        d.getStatus(),
                        d.getAttemptCount(),
                        d.getResponseStatus(),
                        d.getErrorMessage(),
                        d.getDeliveredAt(),
                        d.getCreatedAt()
                ))
                .toList();

        return ResponseEntity.ok(results);
    }
}
