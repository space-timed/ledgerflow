package com.ledgerflow.webhook;

import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.merchant.service.MerchantService;
import com.ledgerflow.webhook.controller.MockWebhookReceiverController;
import com.ledgerflow.webhook.domain.WebhookDelivery;
import com.ledgerflow.webhook.domain.WebhookSubscription;
import com.ledgerflow.webhook.repository.WebhookDeliveryRepository;
import com.ledgerflow.webhook.repository.WebhookSubscriptionRepository;
import com.ledgerflow.webhook.service.WebhookDispatcherService;
import com.ledgerflow.webhook.service.WebhookSigner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebhookSigningAndDeliveryIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private WebhookDispatcherService dispatcherService;

    @Autowired
    private WebhookSubscriptionRepository subscriptionRepository;

    @Autowired
    private WebhookDeliveryRepository deliveryRepository;

    @Autowired
    private MockWebhookReceiverController mockReceiverController;

    @Test
    @DisplayName("HMAC-SHA256 signature generation and constant-time verification")
    void testHmacSigningAndVerification() {
        String secret = "whsec_test_secret_key_1234567890";
        String payload = "{\"event\":\"payment.captured\",\"amount\":5000}";
        long timestamp = Instant.now().getEpochSecond();

        String signatureHeader = WebhookSigner.sign(payload, secret, timestamp);
        assertNotNull(signatureHeader);
        assertTrue(signatureHeader.startsWith("t=" + timestamp + ",v1="));

        // 1. Verify authentic signature matches
        assertTrue(WebhookSigner.verify(payload, secret, signatureHeader));

        // 2. Tampered payload is rejected
        String tamperedPayload = "{\"event\":\"payment.captured\",\"amount\":999999}";
        assertFalse(WebhookSigner.verify(tamperedPayload, secret, signatureHeader));

        // 3. Wrong secret is rejected
        assertFalse(WebhookSigner.verify(payload, "whsec_wrong_secret", signatureHeader));
    }

    @Test
    @DisplayName("Webhook registration, signed dispatch to local mock receiver, and attempt tracking")
    void testWebhookDispatchFlow() {
        MerchantService.MerchantRegistrationResult reg = merchantService.registerMerchant(
                "Webhook Test Merchant",
                "webhook-" + UUID.randomUUID() + "@merchant.com"
        );
        Merchant merchant = reg.merchant();

        String mockReceiverUrl = "http://localhost:" + port + "/api/v1/mock/webhook-receiver";

        // 1. Register subscription
        WebhookDispatcherService.SubscriptionResult sub = dispatcherService.createSubscription(
                merchant,
                mockReceiverUrl,
                List.of("payment.captured", "payment.refunded")
        );

        assertNotNull(sub.id());
        assertNotNull(sub.secretKey());
        assertEquals("ACTIVE", sub.status());

        // 2. Create pending delivery
        WebhookSubscription subscriptionEntity = subscriptionRepository.findById(sub.id()).orElseThrow();
        UUID eventId = UUID.randomUUID();
        WebhookDelivery delivery = new WebhookDelivery(subscriptionEntity, eventId, "payment.captured");
        WebhookDelivery savedDelivery = deliveryRepository.save(delivery);

        // 3. Dispatch delivery
        String payload = "{\"eventId\":\"" + eventId + "\",\"status\":\"CAPTURED\",\"amount\":5000}";
        WebhookDispatcherService.DeliveryResult result = dispatcherService.deliverWebhook(savedDelivery.getId(), payload);

        assertEquals("SUCCESS", result.status());
        assertEquals(200, result.responseStatus());
        assertNotNull(result.deliveredAt());

        // 4. Verify local mock receiver captured the event
        List<MockWebhookReceiverController.ReceivedWebhook> received = mockReceiverController.getDeliveries().getBody();
        assertNotNull(received);
        assertFalse(received.isEmpty());
        assertTrue(received.get(0).signatureHeader().contains("v1="));
    }
}
