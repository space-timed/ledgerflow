package com.ledgerflow.webhook.repository;

import com.ledgerflow.webhook.domain.WebhookDelivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findBySubscriptionIdOrderByCreatedAtDesc(UUID subscriptionId, Pageable pageable);

    @Query("SELECT wd FROM WebhookDelivery wd JOIN wd.subscription s WHERE s.merchant.id = :merchantId ORDER BY wd.createdAt DESC")
    List<WebhookDelivery> findByMerchantIdOrderByCreatedAtDesc(@Param("merchantId") UUID merchantId, Pageable pageable);

    @Query("SELECT wd FROM WebhookDelivery wd WHERE wd.status = 'PENDING' AND (wd.nextRetryAt IS NULL OR wd.nextRetryAt <= :now)")
    List<WebhookDelivery> findPendingDeliveries(@Param("now") Instant now, Pageable pageable);
}
