package com.ledgerflow.refund.repository;

import com.ledgerflow.refund.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {

    List<Refund> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);

    List<Refund> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    Optional<Refund> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);

    @Query("SELECT COALESCE(SUM(r.amountMinorUnits), 0) FROM Refund r WHERE r.payment.id = :paymentId")
    long sumRefundedAmountByPaymentId(@Param("paymentId") UUID paymentId);
}
