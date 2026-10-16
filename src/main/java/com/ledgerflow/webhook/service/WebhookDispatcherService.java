package com.ledgerflow.webhook.service;

import com.ledgerflow.common.error.BusinessException;
import com.ledgerflow.common.error.ErrorCode;
import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.webhook.domain.WebhookDelivery;
import com.ledgerflow.webhook.domain.WebhookSubscription;
import com.ledgerflow.webhook.repository.WebhookDeliveryRepository;
import com.ledgerflow.webhook.repository.WebhookSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class WebhookDispatcherService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcherService.class);
    private static final SecureRandom secureRandom = new SecureRandom();

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final HttpClient httpClient;

    @Value("${ledgerflow.webhook.max-retries:3}")
    private int maxRetries = 3;

    public WebhookDispatcherService(WebhookSubscriptionRepository subscriptionRepository,
                                    WebhookDeliveryRepository deliveryRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public record SubscriptionResult(
            UUID id,
            String targetUrl,
            String secretKey,
            List<String> events,
            String status,
            Instant createdAt
    ) {}

    public record DeliveryResult(
            UUID id,
            UUID eventId,
            String eventType,
            String targetUrl,
            String status,
            int attemptCount,
            Integer responseStatus,
            String errorMessage,
            Instant deliveredAt,
            Instant createdAt
    ) {}

    @Transactional
    public SubscriptionResult createSubscription(Merchant merchant, String targetUrl, List<String> events) {
        validateTargetUrl(targetUrl);

        byte[] secretBytes = new byte[32];
        secureRandom.nextBytes(secretBytes);
        String secretKey = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        WebhookSubscription sub = new WebhookSubscription(merchant, targetUrl.trim(), secretKey, events);
        WebhookSubscription saved = subscriptionRepository.save(sub);

        log.info("Registered webhook subscription [{}] for merchant [{}] targeting [{}]",
                saved.getId(), merchant.getId(), targetUrl);

        return new SubscriptionResult(saved.getId(), saved.getTargetUrl(), saved.getSecretKey(), saved.getEvents(), saved.getStatus(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResult> listSubscriptions(UUID merchantId) {
        return subscriptionRepository.findByMerchantId(merchantId).stream()
                .map(s -> new SubscriptionResult(s.getId(), s.getTargetUrl(), s.getSecretKey(), s.getEvents(), s.getStatus(), s.getCreatedAt()))
                .toList();
    }

    @Transactional
    public DeliveryResult deliverWebhook(UUID deliveryId, String payload) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Webhook delivery not found: " + deliveryId));

        WebhookSubscription sub = delivery.getSubscription();
        long timestamp = Instant.now().getEpochSecond();
        String signatureHeader = WebhookSigner.sign(payload, sub.getSecretKey(), timestamp);

        delivery.setAttemptCount(delivery.getAttemptCount() + 1);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sub.getTargetUrl()))
                    .header("Content-Type", "application/json")
                    .header(WebhookSigner.SIGNATURE_HEADER, signatureHeader)
                    .header("User-Agent", "LedgerFlow-Webhook-Dispatcher/1.0")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(payload != null ? payload : "{}"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            delivery.setResponseStatus(response.statusCode());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivery.setStatus("SUCCESS");
                delivery.setDeliveredAt(Instant.now());
                delivery.setErrorMessage(null);
                log.info("Webhook [{}] delivered successfully to [{}] (status {})",
                        delivery.getId(), sub.getTargetUrl(), response.statusCode());
            } else {
                handleFailure(delivery, "Destination responded with HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            handleFailure(delivery, "Connection error: " + e.getMessage());
        }

        WebhookDelivery saved = deliveryRepository.save(delivery);
        return toDeliveryResult(saved);
    }

    @Transactional
    public DeliveryResult redeliver(UUID deliveryId, UUID merchantId) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Webhook delivery not found: " + deliveryId));

        if (!delivery.getSubscription().getMerchant().getId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Delivery does not belong to merchant");
        }

        delivery.setStatus("PENDING");
        delivery.setNextRetryAt(Instant.now());
        WebhookDelivery saved = deliveryRepository.save(delivery);

        log.info("Queued manual redelivery for webhook delivery [{}]", deliveryId);
        return toDeliveryResult(saved);
    }

    private void handleFailure(WebhookDelivery delivery, String reason) {
        log.warn("Webhook delivery [{}] attempt [{}/{}] failed: {}",
                delivery.getId(), delivery.getAttemptCount(), maxRetries, reason);

        delivery.setErrorMessage(reason);
        if (delivery.getAttemptCount() >= maxRetries) {
            delivery.setStatus("FAILED");
            delivery.setNextRetryAt(null);
        } else {
            // Exponential backoff: 2^attempt * 5 seconds
            long delaySeconds = (long) Math.pow(2, delivery.getAttemptCount()) * 5L;
            delivery.setStatus("PENDING");
            delivery.setNextRetryAt(Instant.now().plusSeconds(delaySeconds));
        }
    }

    private void validateTargetUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PAYLOAD, "Webhook target URL is required");
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new BusinessException(ErrorCode.INVALID_PAYLOAD, "Webhook URL must use HTTP or HTTPS");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_PAYLOAD, "Malformed webhook URL: " + url);
        }
    }

    private DeliveryResult toDeliveryResult(WebhookDelivery d) {
        return new DeliveryResult(
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
        );
    }
}
