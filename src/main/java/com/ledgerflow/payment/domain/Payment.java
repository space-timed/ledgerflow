package com.ledgerflow.payment.domain;

import com.ledgerflow.common.money.CurrencyCode;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.JournalEntry;
import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.wallet.domain.Wallet;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_payment_merchant_idempotency", columnNames = {"merchant_id", "idempotency_key"})
})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "customer_wallet_id", nullable = false)
    private Wallet customerWallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_account_id", nullable = false)
    private Account merchantAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id")
    private JournalEntry journalEntry;

    @Column(name = "amount_minor_units", nullable = false)
    private long amountMinorUnits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyCode currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Payment() {
    }

    public Payment(Merchant merchant,
                   Wallet customerWallet,
                   Account merchantAccount,
                   long amountMinorUnits,
                   CurrencyCode currency,
                   String idempotencyKey) {
        this.merchant = merchant;
        this.customerWallet = customerWallet;
        this.merchantAccount = merchantAccount;
        this.amountMinorUnits = amountMinorUnits;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public Wallet getCustomerWallet() {
        return customerWallet;
    }

    public Account getMerchantAccount() {
        return merchantAccount;
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

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
