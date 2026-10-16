package com.ledgerflow.payment.repository;

import com.ledgerflow.payment.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);
}
