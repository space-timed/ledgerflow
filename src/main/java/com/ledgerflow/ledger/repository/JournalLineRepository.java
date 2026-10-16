package com.ledgerflow.ledger.repository;

import com.ledgerflow.ledger.domain.JournalDirection;
import com.ledgerflow.ledger.domain.JournalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JournalLineRepository extends JpaRepository<JournalLine, UUID> {
    List<JournalLine> findByAccountId(UUID accountId);

    @Query("SELECT COALESCE(SUM(jl.amountMinorUnits), 0) FROM JournalLine jl WHERE jl.account.id = :accountId AND jl.direction = :direction")
    long sumAmountByAccountIdAndDirection(@Param("accountId") UUID accountId, @Param("direction") JournalDirection direction);
}
