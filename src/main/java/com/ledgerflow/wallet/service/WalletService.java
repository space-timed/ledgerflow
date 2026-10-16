package com.ledgerflow.wallet.service;

import com.ledgerflow.common.error.BusinessException;
import com.ledgerflow.common.error.ErrorCode;
import com.ledgerflow.common.money.CurrencyCode;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountType;
import com.ledgerflow.ledger.domain.JournalDirection;
import com.ledgerflow.ledger.domain.JournalEntry;
import com.ledgerflow.ledger.domain.ReferenceType;
import com.ledgerflow.ledger.repository.AccountRepository;
import com.ledgerflow.ledger.service.LedgerService;
import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.wallet.domain.Wallet;
import com.ledgerflow.wallet.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository walletRepository;
    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;

    public WalletService(WalletRepository walletRepository,
                         AccountRepository accountRepository,
                         LedgerService ledgerService) {
        this.walletRepository = walletRepository;
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
    }

    public record WalletBalanceResponse(
            UUID walletId,
            String customerId,
            CurrencyCode currency,
            long balanceMinorUnits,
            String formattedBalance,
            boolean ledgerVerified
    ) {}

    public record SandboxFundingResult(
            UUID walletId,
            UUID journalEntryId,
            long creditedAmount,
            long newBalanceMinorUnits,
            String formattedBalance,
            CurrencyCode currency
    ) {}

    @Transactional
    public Wallet createWallet(Merchant merchant, String customerId, CurrencyCode currency) {
        if (customerId == null || customerId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PAYLOAD, "Customer ID cannot be blank");
        }
        if (currency == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_CURRENCY, "Currency must be specified");
        }

        if (walletRepository.findByMerchantIdAndCustomerIdAndCurrency(merchant.getId(), customerId.trim(), currency).isPresent()) {
            throw new BusinessException(ErrorCode.INVALID_PAYLOAD,
                    String.format("Wallet already exists for customer [%s] with currency [%s]", customerId, currency));
        }

        Account account = new Account(
                merchant,
                AccountType.ASSET,
                currency,
                String.format("Customer Wallet Account [%s] (%s)", customerId, currency)
        );
        Account savedAccount = accountRepository.save(account);

        Wallet wallet = new Wallet(merchant, customerId.trim(), savedAccount, currency);
        Wallet savedWallet = walletRepository.save(wallet);

        log.info("Created customer wallet [{}] for customer [{}] currency [{}] linked to ledger account [{}]",
                savedWallet.getId(), customerId, currency, savedAccount.getId());
        return savedWallet;
    }

    @Transactional(readOnly = true)
    public List<Wallet> listWallets(UUID merchantId) {
        return walletRepository.findByMerchantId(merchantId);
    }

    @Transactional(readOnly = true)
    public Wallet getWallet(UUID id, UUID merchantId) {
        return walletRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Wallet not found: " + id));
    }

    @Transactional(readOnly = true)
    public WalletBalanceResponse getWalletBalance(UUID id, UUID merchantId) {
        Wallet wallet = getWallet(id, merchantId);
        long computedLedgerBalance = ledgerService.calculateAccountBalance(wallet.getAccount().getId(), AccountType.ASSET);

        boolean verified = (computedLedgerBalance == wallet.getBalanceMinorUnits());
        if (!verified) {
            log.error("CRITICAL LEDGER INVARIANT MISMATCH for wallet [{}]: Cached Balance = {}, Ledger Balance = {}",
                    id, wallet.getBalanceMinorUnits(), computedLedgerBalance);
        }

        return new WalletBalanceResponse(
                wallet.getId(),
                wallet.getCustomerId(),
                wallet.getCurrency(),
                wallet.getBalanceMinorUnits(),
                wallet.getCurrency().formatMinorUnits(wallet.getBalanceMinorUnits()),
                verified
        );
    }

    @Transactional
    public SandboxFundingResult fundSandboxWallet(UUID walletId,
                                                 UUID merchantId,
                                                 long amountMinorUnits,
                                                 CurrencyCode currency,
                                                 String description,
                                                 String idempotencyKey) {
        if (amountMinorUnits <= 0) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, "Funding amount must be strictly greater than zero");
        }

        Wallet wallet = walletRepository.findForUpdateByIdAndMerchantId(walletId, merchantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Wallet not found: " + walletId));

        if (wallet.getCurrency() != currency) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_CURRENCY,
                    String.format("Currency mismatch: Wallet currency is %s, but funding requested %s",
                            wallet.getCurrency(), currency));
        }

        Account clearingAccount = ledgerService.getOrCreateSystemClearingAccount(currency);
        Account walletAccount = wallet.getAccount();

        String refId = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : UUID.randomUUID().toString();
        String desc = (description != null && !description.isBlank()) ? description : "Sandbox wallet funding";

        // Balanced Double-Entry Journal:
        // DEBIT: Customer Wallet Account (ASSET) +amount
        // CREDIT: System Clearing Account (EQUITY) +amount
        JournalEntry journal = ledgerService.postJournal(
                refId,
                ReferenceType.SANDBOX_FUNDING,
                desc,
                List.of(
                        new LedgerService.PostingInstruction(walletAccount, JournalDirection.DEBIT, amountMinorUnits, currency),
                        new LedgerService.PostingInstruction(clearingAccount, JournalDirection.CREDIT, amountMinorUnits, currency)
                )
        );

        long newBalance = wallet.getBalanceMinorUnits() + amountMinorUnits;
        wallet.setBalanceMinorUnits(newBalance);
        walletRepository.save(wallet);

        log.info("Funded wallet [{}] with {} {}. New balance: {}. Journal: [{}]",
                wallet.getId(), amountMinorUnits, currency, newBalance, journal.getId());

        return new SandboxFundingResult(
                wallet.getId(),
                journal.getId(),
                amountMinorUnits,
                newBalance,
                currency.formatMinorUnits(newBalance),
                currency
        );
    }
}
