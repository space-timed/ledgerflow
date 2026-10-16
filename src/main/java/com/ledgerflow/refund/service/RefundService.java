package com.ledgerflow.refund.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.common.error.BusinessException;
import com.ledgerflow.common.error.ErrorCode;
import com.ledgerflow.common.money.CurrencyCode;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.JournalDirection;
import com.ledgerflow.ledger.domain.JournalEntry;
import com.ledgerflow.ledger.domain.ReferenceType;
import com.ledgerflow.ledger.service.LedgerService;
import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.outbox.domain.OutboxEvent;
import com.ledgerflow.outbox.repository.OutboxEventRepository;
import com.ledgerflow.payment.domain.Payment;
import com.ledgerflow.payment.domain.PaymentStatus;
import com.ledgerflow.payment.repository.PaymentRepository;
import com.ledgerflow.refund.domain.Refund;
import com.ledgerflow.refund.repository.RefundRepository;
import com.ledgerflow.wallet.domain.Wallet;
import com.ledgerflow.wallet.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public RefundService(PaymentRepository paymentRepository,
                         RefundRepository refundRepository,
                         WalletRepository walletRepository,
                         LedgerService ledgerService,
                         OutboxEventRepository outboxEventRepository,
                         ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.walletRepository = walletRepository;
        this.ledgerService = ledgerService;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public record RefundResult(
            UUID refundId,
            UUID paymentId,
            long amountMinorUnits,
            String formattedAmount,
            CurrencyCode currency,
            String status,
            String reason,
            long totalRefundedMinorUnits,
            long remainingRefundableMinorUnits,
            PaymentStatus paymentStatus,
            UUID journalEntryId,
            Instant createdAt
    ) {}

    @Transactional
    public RefundResult processRefund(Merchant merchant,
                                      UUID paymentId,
                                      long amountMinorUnits,
                                      String reason,
                                      String idempotencyKey) {
        if (amountMinorUnits <= 0) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, "Refund amount must be strictly greater than zero minor units");
        }

        // 1. Pessimistic lock on payment
        Payment payment = paymentRepository.findForUpdateById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Payment not found: " + paymentId));

        if (!payment.getMerchant().getId().equals(merchant.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Payment does not belong to merchant");
        }

        if (payment.getStatus() != PaymentStatus.CAPTURED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_IN_REFUNDABLE_STATE,
                    "Payment cannot be refunded in status: " + payment.getStatus());
        }

        // 2. Validate refund capacity
        long previouslyRefunded = refundRepository.sumRefundedAmountByPaymentId(payment.getId());
        long remainingRefundable = payment.getAmountMinorUnits() - previouslyRefunded;

        if (amountMinorUnits > remainingRefundable) {
            throw new BusinessException(ErrorCode.REFUND_EXCEEDS_CAPACITY,
                    String.format("Requested refund %s exceeds remaining refundable balance %s (Original: %s, Already Refunded: %s)",
                            payment.getCurrency().formatMinorUnits(amountMinorUnits),
                            payment.getCurrency().formatMinorUnits(remainingRefundable),
                            payment.getCurrency().formatMinorUnits(payment.getAmountMinorUnits()),
                            payment.getCurrency().formatMinorUnits(previouslyRefunded)));
        }

        // 3. Pessimistic lock on customer wallet
        Wallet wallet = walletRepository.findForUpdateByIdAndMerchantId(payment.getCustomerWallet().getId(), merchant.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Customer wallet not found"));

        Account merchantSettlementAccount = payment.getMerchantAccount();

        // 4. Post reversal double-entry journal:
        // DEBIT: Merchant Settlement Account (LIABILITY) - decreases merchant payable
        // CREDIT: Customer Wallet Account (ASSET) - increases customer wallet asset
        String refundRef = "ref_" + UUID.randomUUID();
        String desc = (reason != null && !reason.isBlank()) ? reason : "Payment refund";

        JournalEntry reversalJournal = ledgerService.postJournal(
                refundRef,
                ReferenceType.REFUND,
                desc,
                List.of(
                        new LedgerService.PostingInstruction(wallet.getAccount(), JournalDirection.DEBIT, amountMinorUnits, payment.getCurrency()),
                        new LedgerService.PostingInstruction(merchantSettlementAccount, JournalDirection.CREDIT, amountMinorUnits, payment.getCurrency())
                )
        );

        // 5. Update wallet balance
        wallet.setBalanceMinorUnits(wallet.getBalanceMinorUnits() + amountMinorUnits);
        walletRepository.save(wallet);

        // 6. Update payment status
        long totalRefundedAfter = previouslyRefunded + amountMinorUnits;
        if (totalRefundedAfter == payment.getAmountMinorUnits()) {
            payment.setStatus(PaymentStatus.REFUNDED);
        } else {
            payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }
        paymentRepository.save(payment);

        // 7. Save refund record
        Refund refund = new Refund(
                payment,
                merchant,
                amountMinorUnits,
                payment.getCurrency(),
                reason,
                idempotencyKey
        );
        refund.setJournalEntry(reversalJournal);
        Refund savedRefund = refundRepository.save(refund);

        // 8. Record outbox event
        recordOutboxEvent("REFUND", savedRefund.getId().toString(), "payment.refunded", savedRefund, payment);

        log.info("Processed refund [{}] of {} {} for payment [{}]. New payment status: {}. Journal: [{}]",
                savedRefund.getId(), amountMinorUnits, payment.getCurrency(), payment.getId(), payment.getStatus(), reversalJournal.getId());

        return new RefundResult(
                savedRefund.getId(),
                payment.getId(),
                amountMinorUnits,
                payment.getCurrency().formatMinorUnits(amountMinorUnits),
                payment.getCurrency(),
                savedRefund.getStatus(),
                savedRefund.getReason(),
                totalRefundedAfter,
                payment.getAmountMinorUnits() - totalRefundedAfter,
                payment.getStatus(),
                reversalJournal.getId(),
                savedRefund.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<RefundResult> listRefundsForPayment(UUID paymentId, UUID merchantId) {
        Payment payment = paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Payment not found: " + paymentId));

        return refundRepository.findByPaymentIdOrderByCreatedAtDesc(payment.getId()).stream()
                .map(r -> new RefundResult(
                        r.getId(),
                        payment.getId(),
                        r.getAmountMinorUnits(),
                        r.getCurrency().formatMinorUnits(r.getAmountMinorUnits()),
                        r.getCurrency(),
                        r.getStatus(),
                        r.getReason(),
                        0,
                        0,
                        payment.getStatus(),
                        r.getJournalEntry() != null ? r.getJournalEntry().getId() : null,
                        r.getCreatedAt()
                ))
                .toList();
    }

    private void recordOutboxEvent(String aggregateType, String aggregateId, String eventType, Refund refund, Payment payment) {
        try {
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("eventId", UUID.randomUUID().toString());
            payloadMap.put("refundId", refund.getId().toString());
            payloadMap.put("paymentId", payment.getId().toString());
            payloadMap.put("merchantId", refund.getMerchant().getId().toString());
            payloadMap.put("amountMinorUnits", refund.getAmountMinorUnits());
            payloadMap.put("currency", refund.getCurrency().name());
            payloadMap.put("paymentStatus", payment.getStatus().name());
            payloadMap.put("reason", refund.getReason());
            payloadMap.put("idempotencyKey", refund.getIdempotencyKey());
            payloadMap.put("journalEntryId", refund.getJournalEntry() != null ? refund.getJournalEntry().getId().toString() : null);
            payloadMap.put("timestamp", Instant.now().toString());

            String jsonPayload = objectMapper.writeValueAsString(payloadMap);
            outboxEventRepository.save(new OutboxEvent(aggregateType, aggregateId, eventType, jsonPayload));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event for refund [{}]", refund.getId(), e);
        }
    }
}
