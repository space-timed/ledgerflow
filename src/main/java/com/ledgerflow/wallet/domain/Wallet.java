package com.ledgerflow.wallet.domain;

import com.ledgerflow.common.money.CurrencyCode;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.merchant.domain.Merchant;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets", uniqueConstraints = {
        @UniqueConstraint(name = "uq_merchant_customer_currency", columnNames = {"merchant_id", "customer_id", "currency"})
})
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyCode currency;

    @Column(name = "balance_minor_units", nullable = false)
    private long balanceMinorUnits = 0;

    @Version
    @Column(nullable = false)
    private long version = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Wallet() {
    }

    public Wallet(Merchant merchant, String customerId, Account account, CurrencyCode currency) {
        this.merchant = merchant;
        this.customerId = customerId;
        this.account = account;
        this.currency = currency;
        this.balanceMinorUnits = 0;
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

    public String getCustomerId() {
        return customerId;
    }

    public Account getAccount() {
        return account;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public long getBalanceMinorUnits() {
        return balanceMinorUnits;
    }

    public void setBalanceMinorUnits(long balanceMinorUnits) {
        this.balanceMinorUnits = balanceMinorUnits;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
