package com.ledgerflow.webhook.domain;

import com.ledgerflow.merchant.domain.Merchant;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "webhook_subscriptions")
public class WebhookSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "target_url", nullable = false, length = 1024)
    private String targetUrl;

    @Column(name = "secret_key", nullable = false, length = 64)
    private String secretKey;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY)
    @Column(name = "events", columnDefinition = "varchar(255) array")
    private List<String> events = new ArrayList<>();

    @Column(nullable = false, length = 50)
    private String status = "ACTIVE"; // ACTIVE, DISABLED

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public WebhookSubscription() {
    }

    public WebhookSubscription(Merchant merchant, String targetUrl, String secretKey, List<String> events) {
        this.merchant = merchant;
        this.targetUrl = targetUrl;
        this.secretKey = secretKey;
        this.events = (events != null) ? new ArrayList<>(events) : new ArrayList<>();
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public List<String> getEvents() {
        return events;
    }

    public void setEvents(List<String> events) {
        this.events = events;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
