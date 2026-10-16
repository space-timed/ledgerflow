package com.ledgerflow.merchant.repository;

import com.ledgerflow.merchant.domain.ApiKey;
import com.ledgerflow.merchant.domain.MerchantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    @Query("SELECT k FROM ApiKey k JOIN FETCH k.merchant WHERE k.keyHash = :keyHash AND k.status = :status")
    Optional<ApiKey> findByKeyHashAndStatus(@Param("keyHash") String keyHash, @Param("status") MerchantStatus status);

    List<ApiKey> findByMerchantId(UUID merchantId);
}
