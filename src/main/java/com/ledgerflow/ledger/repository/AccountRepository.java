package com.ledgerflow.ledger.repository;

import com.ledgerflow.common.money.CurrencyCode;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByMerchantId(UUID merchantId);
    Optional<Account> findByMerchantIdAndAccountTypeAndCurrency(UUID merchantId, AccountType accountType, CurrencyCode currency);
    Optional<Account> findByMerchantIsNullAndAccountTypeAndCurrency(AccountType accountType, CurrencyCode currency);
}
