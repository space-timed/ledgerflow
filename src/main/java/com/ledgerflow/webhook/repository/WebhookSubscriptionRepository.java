package com.ledgerflow.webhook.repository;

import com.ledgerflow.webhook.domain.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {
    List<WebhookSubscription> findByMerchantId(UUID merchantId);
    List<WebhookSubscription> findByMerchantIdAndStatus(UUID merchantId, String status);
}
