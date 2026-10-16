package com.ledgerflow.reconciliation.repository;

import com.ledgerflow.reconciliation.domain.ReconciliationDiscrepancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReconciliationDiscrepancyRepository extends JpaRepository<ReconciliationDiscrepancy, UUID> {
    List<ReconciliationDiscrepancy> findByRunId(UUID runId);
}
