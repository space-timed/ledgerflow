package com.ledgerflow.payment.domain;

import com.ledgerflow.merchant.domain.Merchant;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = {
        @UniqueConstraint(name = "uq_idempotency_merchant_key", columnNames = {"merchant_id", "idempotency_key"})
})
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false, length = 50)
    private String status = "IN_PROGRESS"; // IN_PROGRESS, COMPLETED, FAILED

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "locked_until", nullable = false)
    private Instant lockedUntil;

    public IdempotencyRecord() {
    }

    public IdempotencyRecord(Merchant merchant, String idempotencyKey, String requestHash, Instant lockedUntil) {
        this.merchant = merchant;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = "IN_PROGRESS";
        this.lockedUntil = lockedUntil;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(Integer responseCode) {
        this.responseCode = responseCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }
}
