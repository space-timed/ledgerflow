package com.ledgerflow.ledger.service;

import com.ledgerflow.common.error.BusinessException;
import com.ledgerflow.common.error.ErrorCode;
import com.ledgerflow.common.money.CurrencyCode;
import com.ledgerflow.ledger.domain.*;
import com.ledgerflow.ledger.repository.AccountRepository;
import com.ledgerflow.ledger.repository.JournalEntryRepository;
import com.ledgerflow.ledger.repository.JournalLineRepository;
import com.ledgerflow.merchant.domain.Merchant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalLineRepository journalLineRepository;

    public LedgerService(AccountRepository accountRepository,
                         JournalEntryRepository journalEntryRepository,
                         JournalLineRepository journalLineRepository) {
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.journalLineRepository = journalLineRepository;
    }

    public record PostingInstruction(
            Account account,
            JournalDirection direction,
            long amountMinorUnits,
            CurrencyCode currency
    ) {}

    @Transactional
    public JournalEntry postJournal(String referenceId,
                                    ReferenceType referenceType,
                                    String description,
                                    List<PostingInstruction> instructions) {
        if (instructions == null || instructions.size() < 2) {
            throw new BusinessException(ErrorCode.UNBALANCED_JOURNAL_ENTRY,
                    "A double-entry journal must contain at least two postings (debit and credit).");
        }

        CurrencyCode expectedCurrency = instructions.get(0).currency();
        long totalDebits = 0;
        long totalCredits = 0;

        for (PostingInstruction inst : instructions) {
            if (inst.amountMinorUnits() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_AMOUNT,
                        "Posting amount must be strictly greater than zero minor units. Received: " + inst.amountMinorUnits());
            }

            if (inst.currency() != expectedCurrency) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_CURRENCY,
                        "Multi-currency journal entries are not supported in single transaction. Expected: "
                                + expectedCurrency + ", found: " + inst.currency());
            }

            if (inst.direction() == JournalDirection.DEBIT) {
                totalDebits += inst.amountMinorUnits();
            } else if (inst.direction() == JournalDirection.CREDIT) {
                totalCredits += inst.amountMinorUnits();
            }
        }

        // Invariant: sum(Debits) == sum(Credits)
        if (totalDebits != totalCredits) {
            log.error("DOUBLE-ENTRY INVARIANT VIOLATION for reference [{}]: Debits = {} {}, Credits = {} {}",
                    referenceId, totalDebits, expectedCurrency, totalCredits, expectedCurrency);
            throw new BusinessException(ErrorCode.UNBALANCED_JOURNAL_ENTRY,
                    String.format("Unbalanced journal entry: Total debits (%d %s) != Total credits (%d %s)",
                            totalDebits, expectedCurrency, totalCredits, expectedCurrency));
        }

        JournalEntry entry = new JournalEntry(referenceId, referenceType, description);

        for (PostingInstruction inst : instructions) {
            JournalLine line = new JournalLine(inst.account(), inst.direction(), inst.amountMinorUnits(), inst.currency());
            entry.addLine(line);
        }

        JournalEntry saved = journalEntryRepository.save(entry);
        log.debug("Successfully posted balanced journal [{}] for reference [{}] - Balanced amount: {} {}",
                saved.getId(), referenceId, totalDebits, expectedCurrency);
        return saved;
    }

    @Transactional
    public Account getOrCreateSystemClearingAccount(CurrencyCode currency) {
        return accountRepository.findByMerchantIsNullAndAccountTypeAndCurrency(AccountType.EQUITY, currency)
                .orElseGet(() -> {
                    Account clearing = new Account(null, AccountType.EQUITY, currency, "System Sandbox Clearing Account (" + currency + ")");
                    return accountRepository.save(clearing);
                });
    }

    @Transactional
    public Account getOrCreateMerchantSettlementAccount(Merchant merchant, CurrencyCode currency) {
        return accountRepository.findByMerchantIdAndAccountTypeAndCurrency(merchant.getId(), AccountType.LIABILITY, currency)
                .orElseGet(() -> {
                    Account settlement = new Account(merchant, AccountType.LIABILITY, currency, "Merchant Settlement Account (" + currency + ")");
                    return accountRepository.save(settlement);
                });
    }

    @Transactional(readOnly = true)
    public long calculateAccountBalance(UUID accountId, AccountType accountType) {
        long debits = journalLineRepository.sumAmountByAccountIdAndDirection(accountId, JournalDirection.DEBIT);
        long credits = journalLineRepository.sumAmountByAccountIdAndDirection(accountId, JournalDirection.CREDIT);

        return switch (accountType) {
            case ASSET, EXPENSE -> debits - credits;
            case LIABILITY, EQUITY, REVENUE -> credits - debits;
        };
    }

    @Transactional(readOnly = true)
    public JournalEntry getJournalEntry(UUID id) {
        return journalEntryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Journal entry not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<JournalEntry> getRecentJournals() {
        return journalEntryRepository.findTop50ByOrderByPostedAtDesc();
    }
}
