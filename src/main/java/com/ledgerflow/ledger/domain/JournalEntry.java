package com.ledgerflow.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "journal_entries")
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "reference_id", nullable = false, length = 100)
    private String referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 50)
    private ReferenceType referenceType;

    @Column(nullable = false)
    private String description;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<JournalLine> lines = new ArrayList<>();

    public JournalEntry() {
    }

    public JournalEntry(String referenceId, ReferenceType referenceType, String description) {
        this.referenceId = referenceId;
        this.referenceType = referenceType;
        this.description = description;
        this.postedAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public void addLine(JournalLine line) {
        lines.add(line);
        line.setJournalEntry(this);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public String getDescription() {
        return description;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<JournalLine> getLines() {
        return lines;
    }
}
