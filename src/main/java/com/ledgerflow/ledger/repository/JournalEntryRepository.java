package com.ledgerflow.ledger.repository;

import com.ledgerflow.ledger.domain.JournalEntry;
import com.ledgerflow.ledger.domain.ReferenceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    Optional<JournalEntry> findByReferenceTypeAndReferenceId(ReferenceType referenceType, String referenceId);
    List<JournalEntry> findByReferenceType(ReferenceType referenceType);
}
