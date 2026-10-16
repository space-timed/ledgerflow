package com.ledgerflow.ledger;

import com.ledgerflow.common.error.BusinessException;
import com.ledgerflow.common.error.ErrorCode;
import com.ledgerflow.common.money.CurrencyCode;
import com.ledgerflow.ledger.domain.*;
import com.ledgerflow.ledger.repository.AccountRepository;
import com.ledgerflow.ledger.repository.JournalEntryRepository;
import com.ledgerflow.ledger.repository.JournalLineRepository;
import com.ledgerflow.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceDoubleEntryInvariantTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private JournalEntryRepository journalEntryRepository;
    @Mock
    private JournalLineRepository journalLineRepository;

    private LedgerService ledgerService;

    private Account assetAccount;
    private Account liabilityAccount;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(accountRepository, journalEntryRepository, journalLineRepository);

        assetAccount = new Account(null, AccountType.ASSET, CurrencyCode.INR, "Customer Asset");
        assetAccount.setId(UUID.randomUUID());

        liabilityAccount = new Account(null, AccountType.LIABILITY, CurrencyCode.INR, "Merchant Liability");
        liabilityAccount.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Balanced journal posting successfully satisfies sum(Debits) == sum(Credits)")
    void testBalancedPostingSucceeds() {
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<LedgerService.PostingInstruction> instructions = List.of(
                new LedgerService.PostingInstruction(assetAccount, JournalDirection.DEBIT, 5000, CurrencyCode.INR),
                new LedgerService.PostingInstruction(liabilityAccount, JournalDirection.CREDIT, 5000, CurrencyCode.INR)
        );

        JournalEntry result = ledgerService.postJournal(
                "ref_123",
                ReferenceType.PAYMENT,
                "Test Payment",
                instructions
        );

        assertNotNull(result);
        assertEquals(2, result.getLines().size());
        verify(journalEntryRepository, times(1)).save(any(JournalEntry.class));
    }

    @Test
    @DisplayName("Unbalanced journal throws UNBALANCED_JOURNAL_ENTRY and rolls back")
    void testUnbalancedPostingFails() {
        List<LedgerService.PostingInstruction> instructions = List.of(
                new LedgerService.PostingInstruction(assetAccount, JournalDirection.DEBIT, 5000, CurrencyCode.INR),
                new LedgerService.PostingInstruction(liabilityAccount, JournalDirection.CREDIT, 4000, CurrencyCode.INR)
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                ledgerService.postJournal("ref_bad", ReferenceType.PAYMENT, "Unbalanced", instructions)
        );

        assertEquals(ErrorCode.UNBALANCED_JOURNAL_ENTRY, ex.getErrorCode());
        verify(journalEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Negative or zero amounts are rejected with INVALID_AMOUNT")
    void testNegativeOrZeroAmountRejected() {
        List<LedgerService.PostingInstruction> zeroInstructions = List.of(
                new LedgerService.PostingInstruction(assetAccount, JournalDirection.DEBIT, 0, CurrencyCode.INR),
                new LedgerService.PostingInstruction(liabilityAccount, JournalDirection.CREDIT, 0, CurrencyCode.INR)
        );

        BusinessException ex1 = assertThrows(BusinessException.class, () ->
                ledgerService.postJournal("ref_zero", ReferenceType.PAYMENT, "Zero", zeroInstructions)
        );
        assertEquals(ErrorCode.INVALID_AMOUNT, ex1.getErrorCode());

        List<LedgerService.PostingInstruction> negativeInstructions = List.of(
                new LedgerService.PostingInstruction(assetAccount, JournalDirection.DEBIT, -100, CurrencyCode.INR),
                new LedgerService.PostingInstruction(liabilityAccount, JournalDirection.CREDIT, -100, CurrencyCode.INR)
        );

        BusinessException ex2 = assertThrows(BusinessException.class, () ->
                ledgerService.postJournal("ref_neg", ReferenceType.PAYMENT, "Negative", negativeInstructions)
        );
        assertEquals(ErrorCode.INVALID_AMOUNT, ex2.getErrorCode());
    }

    @Test
    @DisplayName("Cross-currency single postings are rejected with UNSUPPORTED_CURRENCY")
    void testMixedCurrencyRejected() {
        List<LedgerService.PostingInstruction> mixedInstructions = List.of(
                new LedgerService.PostingInstruction(assetAccount, JournalDirection.DEBIT, 100, CurrencyCode.INR),
                new LedgerService.PostingInstruction(liabilityAccount, JournalDirection.CREDIT, 100, CurrencyCode.USD)
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                ledgerService.postJournal("ref_mixed", ReferenceType.PAYMENT, "Mixed", mixedInstructions)
        );
        assertEquals(ErrorCode.UNSUPPORTED_CURRENCY, ex.getErrorCode());
    }
}
