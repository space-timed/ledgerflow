package com.ledgerflow.refund.domain;

import com.ledgerflow.common.money.CurrencyCode;
import com.ledgerflow.ledger.domain.JournalEntry;
import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.payment.domain.Payment;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds", uniqueConstraints = {
        @UniqueConstraint(name = "uq_refund_merchant_idempotency", columnNames = {"merchant_id", "idempotency_key"})
})
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id")
    private JournalEntry journalEntry;

    @Column(name = "amount_minor_units", nullable = false)
    private long amountMinorUnits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyCode currency;

    @Column(nullable = false, length = 50)
    private String status = "COMPLETED";

    private String reason;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Refund() {
    }

    public Refund(Payment payment,
                  Merchant merchant,
                  long amountMinorUnits,
                  CurrencyCode currency,
                  String reason,
                  String idempotencyKey) {
        this.payment = payment;
        this.merchant = merchant;
        this.amountMinorUnits = amountMinorUnits;
        this.currency = currency;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
        this.status = "COMPLETED";
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Payment getPayment() {
        return payment;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public JournalEntry getJournalEntry() {
        return journalEntry;
    }

    public void setJournalEntry(JournalEntry journalEntry) {
        this.journalEntry = journalEntry;
    }

    public long getAmountMinorUnits() {
        return amountMinorUnits;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
