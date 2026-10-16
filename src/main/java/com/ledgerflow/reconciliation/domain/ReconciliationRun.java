package com.ledgerflow.reconciliation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String status = "RUNNING"; // RUNNING, COMPLETED, FAILED

    @Column(name = "checked_journals_count", nullable = false)
    private long checkedJournalsCount = 0;

    @Column(name = "checked_wallets_count", nullable = false)
    private long checkedWalletsCount = 0;

    @Column(name = "discrepancies_count", nullable = false)
    private int discrepanciesCount = 0;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ReconciliationDiscrepancy> discrepancies = new ArrayList<>();

    public ReconciliationRun() {
    }

    public void addDiscrepancy(ReconciliationDiscrepancy discrepancy) {
        discrepancies.add(discrepancy);
        discrepancy.setRun(this);
        this.discrepanciesCount = discrepancies.size();
    }

    public UUID getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCheckedJournalsCount() {
        return checkedJournalsCount;
    }

    public void setCheckedJournalsCount(long checkedJournalsCount) {
        this.checkedJournalsCount = checkedJournalsCount;
    }

    public long getCheckedWalletsCount() {
        return checkedWalletsCount;
    }

    public void setCheckedWalletsCount(long checkedWalletsCount) {
        this.checkedWalletsCount = checkedWalletsCount;
    }

    public int getDiscrepanciesCount() {
        return discrepanciesCount;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public List<ReconciliationDiscrepancy> getDiscrepancies() {
        return discrepancies;
    }
}
