package com.ledgerflow.wallet.repository;

import com.ledgerflow.common.money.CurrencyCode;
import com.ledgerflow.wallet.domain.Wallet;
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
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByMerchantIdAndCustomerIdAndCurrency(UUID merchantId, String customerId, CurrencyCode currency);

    Optional<Wallet> findByIdAndMerchantId(UUID id, UUID merchantId);

    List<Wallet> findByMerchantId(UUID merchantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findForUpdateById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id AND w.merchant.id = :merchantId")
    Optional<Wallet> findForUpdateByIdAndMerchantId(@Param("id") UUID id, @Param("merchantId") UUID merchantId);
}
