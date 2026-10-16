package com.ledgerflow.reconciliation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.common.error.BusinessException;
import com.ledgerflow.common.error.ErrorCode;
import com.ledgerflow.ledger.domain.AccountType;
import com.ledgerflow.ledger.domain.JournalDirection;
import com.ledgerflow.ledger.domain.JournalEntry;
import com.ledgerflow.ledger.domain.JournalLine;
import com.ledgerflow.ledger.repository.JournalEntryRepository;
import com.ledgerflow.ledger.service.LedgerService;
import com.ledgerflow.payment.domain.Payment;
import com.ledgerflow.payment.domain.PaymentStatus;
import com.ledgerflow.payment.repository.PaymentRepository;
import com.ledgerflow.reconciliation.domain.ReconciliationDiscrepancy;
import com.ledgerflow.reconciliation.domain.ReconciliationRun;
import com.ledgerflow.reconciliation.repository.ReconciliationRunRepository;
import com.ledgerflow.refund.domain.Refund;
import com.ledgerflow.refund.repository.RefundRepository;
import com.ledgerflow.wallet.domain.Wallet;
import com.ledgerflow.wallet.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ReconciliationRunRepository runRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final WalletRepository walletRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    public ReconciliationService(ReconciliationRunRepository runRepository,
                                 JournalEntryRepository journalEntryRepository,
                                 WalletRepository walletRepository,
                                 PaymentRepository paymentRepository,
                                 RefundRepository refundRepository,
                                 LedgerService ledgerService,
                                 ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.walletRepository = walletRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
    }

    public record DiscrepancyReport(
            UUID id,
            String discrepancyType,
            String entityId,
            String description,
            String details,
            Instant detectedAt
    ) {}

    public record ReconciliationReport(
            UUID runId,
            String status,
            long checkedJournalsCount,
            long checkedWalletsCount,
            int discrepanciesCount,
            boolean isHealthy,
            Instant startedAt,
            Instant completedAt,
            List<DiscrepancyReport> discrepancies
    ) {}

    @Transactional
    public ReconciliationReport runReconciliation(boolean simulateAnomaly) {
        log.info("Starting independent financial reconciliation audit (simulateAnomaly: {})...", simulateAnomaly);

        ReconciliationRun run = new ReconciliationRun();
        ReconciliationRun savedRun = runRepository.save(run);

        // 1. Audit Journal Invariant: sum(debits) == sum(credits)
        List<JournalEntry> journals = journalEntryRepository.findAll();
        savedRun.setCheckedJournalsCount(journals.size());

        for (JournalEntry journal : journals) {
            long debits = journal.getLines().stream()
                    .filter(l -> l.getDirection() == JournalDirection.DEBIT)
                    .mapToLong(JournalLine::getAmountMinorUnits)
                    .sum();
            long credits = journal.getLines().stream()
                    .filter(l -> l.getDirection() == JournalDirection.CREDIT)
                    .mapToLong(JournalLine::getAmountMinorUnits)
                    .sum();

            if (debits != credits) {
                String details = toJson(Map.of("journalId", journal.getId(), "debits", debits, "credits", credits));
                savedRun.addDiscrepancy(new ReconciliationDiscrepancy(
                        "UNBALANCED_JOURNAL",
                        journal.getId().toString(),
                        String.format("Journal [%s] is unbalanced: Debits (%d) != Credits (%d)", journal.getId(), debits, credits),
                        details
                ));
            }
        }

        // 2. Audit Customer Wallets vs Ledger History
        List<Wallet> wallets = walletRepository.findAll();
        savedRun.setCheckedWalletsCount(wallets.size());

        for (Wallet wallet : wallets) {
            long calculatedLedgerBalance = ledgerService.calculateAccountBalance(wallet.getAccount().getId(), AccountType.ASSET);
            if (wallet.getBalanceMinorUnits() != calculatedLedgerBalance) {
                String details = toJson(Map.of(
                        "walletId", wallet.getId(),
                        "cachedBalance", wallet.getBalanceMinorUnits(),
                        "ledgerBalance", calculatedLedgerBalance
                ));
                savedRun.addDiscrepancy(new ReconciliationDiscrepancy(
                        "WALLET_BALANCE_MISMATCH",
                        wallet.getId().toString(),
                        String.format("Wallet [%s] cached balance (%d) differs from ledger balance (%d)",
                                wallet.getId(), wallet.getBalanceMinorUnits(), calculatedLedgerBalance),
                        details
                ));
            }
        }

        // 3. Audit Captured Payments vs Journal Postings
        List<Payment> payments = paymentRepository.findAll();
        for (Payment payment : payments) {
            if (payment.getStatus() == PaymentStatus.CAPTURED ||
                payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED ||
                payment.getStatus() == PaymentStatus.REFUNDED) {

                if (payment.getJournalEntry() == null) {
                    savedRun.addDiscrepancy(new ReconciliationDiscrepancy(
                            "MISSING_PAYMENT_JOURNAL",
                            payment.getId().toString(),
                            "Captured payment " + payment.getId() + " is missing linked journal entry",
                            null
                    ));
                }
            }
        }

        // 4. Audit Refund Totals against Original Captures
        for (Payment payment : payments) {
            long totalRefunded = refundRepository.sumRefundedAmountByPaymentId(payment.getId());
            if (totalRefunded > payment.getAmountMinorUnits()) {
                String details = toJson(Map.of(
                        "paymentId", payment.getId(),
                        "capturedAmount", payment.getAmountMinorUnits(),
                        "refundedAmount", totalRefunded
                ));
                savedRun.addDiscrepancy(new ReconciliationDiscrepancy(
                        "EXCESS_REFUND",
                        payment.getId().toString(),
                        String.format("Payment [%s] total refunds (%d) exceed captured amount (%d)",
                                payment.getId(), totalRefunded, payment.getAmountMinorUnits()),
                        details
                ));
            }
        }

        // 5. Test Fixture: Inject simulated discrepancy if explicitly requested
        if (simulateAnomaly) {
            savedRun.addDiscrepancy(new ReconciliationDiscrepancy(
                    "SIMULATED_TEST_ANOMALY",
                    "fixture-test-account-001",
                    "Simulated ledger variance of 100 minor units injected for demonstration",
                    toJson(Map.of("variance", 100, "simulated", true))
            ));
        }

        savedRun.setCompletedAt(Instant.now());
        savedRun.setStatus(savedRun.getDiscrepanciesCount() == 0 ? "PASSED" : "DISCREPANCIES_DETECTED");
        ReconciliationRun completedRun = runRepository.save(savedRun);

        log.info("Reconciliation run [{}] finished with status: [{}]. Checked {} journals, {} wallets, {} discrepancies.",
                completedRun.getId(), completedRun.getStatus(), completedRun.getCheckedJournalsCount(),
                completedRun.getCheckedWalletsCount(), completedRun.getDiscrepanciesCount());

        return toReport(completedRun);
    }

    @Transactional(readOnly = true)
    public List<ReconciliationReport> listRuns() {
        return runRepository.findAllByOrderByStartedAtDesc().stream()
                .map(this::toReport)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReconciliationReport getRun(UUID id) {
        ReconciliationRun run = runRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Reconciliation run not found: " + id));
        return toReport(run);
    }

    private ReconciliationReport toReport(ReconciliationRun run) {
        List<DiscrepancyReport> discrepancyReports = run.getDiscrepancies().stream()
                .map(d -> new DiscrepancyReport(
                        d.getId(),
                        d.getDiscrepancyType(),
                        d.getEntityId(),
                        d.getDescription(),
                        d.getDetails(),
                        d.getDetectedAt()
                ))
                .toList();

        return new ReconciliationReport(
                run.getId(),
                run.getStatus(),
                run.getCheckedJournalsCount(),
                run.getCheckedWalletsCount(),
                run.getDiscrepanciesCount(),
                (run.getDiscrepanciesCount() == 0),
                run.getStartedAt(),
                run.getCompletedAt(),
                discrepancyReports
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
