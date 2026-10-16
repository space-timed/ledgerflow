package com.ledgerflow.reconciliation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_discrepancies")
public class ReconciliationDiscrepancy {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private ReconciliationRun run;

    @Column(name = "discrepancy_type", nullable = false, length = 100)
    private String discrepancyType;

    @Column(name = "entity_id", nullable = false, length = 100)
    private String entityId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt = Instant.now();

    public ReconciliationDiscrepancy() {
    }

    public ReconciliationDiscrepancy(String discrepancyType, String entityId, String description, String details) {
        this.discrepancyType = discrepancyType;
        this.entityId = entityId;
        this.description = description;
        this.details = details;
        this.detectedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ReconciliationRun getRun() {
        return run;
    }

    public void setRun(ReconciliationRun run) {
        this.run = run;
    }

    public String getDiscrepancyType() {
        return discrepancyType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getDescription() {
        return description;
    }

    public String getDetails() {
        return details;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }
}
