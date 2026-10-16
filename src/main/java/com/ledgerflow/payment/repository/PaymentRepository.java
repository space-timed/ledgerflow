package com.ledgerflow.payment.repository;

import com.ledgerflow.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<Payment> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);

    List<Payment> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findForUpdateById(@Param("id") UUID id);
}
