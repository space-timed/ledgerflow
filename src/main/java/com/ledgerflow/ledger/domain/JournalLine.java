package com.ledgerflow.ledger.domain;

import com.ledgerflow.common.money.CurrencyCode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "journal_lines")
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private JournalDirection direction;

    @Column(name = "amount_minor_units", nullable = false)
    private long amountMinorUnits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyCode currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public JournalLine() {
    }

    public JournalLine(Account account, JournalDirection direction, long amountMinorUnits, CurrencyCode currency) {
        this.account = account;
        this.direction = direction;
        this.amountMinorUnits = amountMinorUnits;
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public JournalEntry getJournalEntry() {
        return journalEntry;
    }

    public void setJournalEntry(JournalEntry journalEntry) {
        this.journalEntry = journalEntry;
    }

    public Account getAccount() {
        return account;
    }

    public JournalDirection getDirection() {
        return direction;
    }

    public long getAmountMinorUnits() {
        return amountMinorUnits;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
