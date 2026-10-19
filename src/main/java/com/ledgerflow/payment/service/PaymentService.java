package com.ledgerflow.payment.service;

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
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final com.ledgerflow.common.metrics.LedgerFlowMetrics metrics;

    public PaymentService(PaymentRepository paymentRepository,
                          WalletRepository walletRepository,
                          LedgerService ledgerService,
                          OutboxEventRepository outboxEventRepository,
                          ObjectMapper objectMapper,
                          com.ledgerflow.common.metrics.LedgerFlowMetrics metrics) {
        this.paymentRepository = paymentRepository;
        this.walletRepository = walletRepository;
        this.ledgerService = ledgerService;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public record PaymentResult(
            UUID id,
            String customerId,
            UUID customerWalletId,
            long amountMinorUnits,
            String formattedAmount,
            CurrencyCode currency,
            PaymentStatus status,
            String failureReason,
            String idempotencyKey,
            UUID journalEntryId,
            Instant createdAt
    ) {}

    @Transactional
    public PaymentResult processPayment(Merchant merchant,
                                        UUID customerWalletId,
                                        long amountMinorUnits,
                                        CurrencyCode currency,
                                        String description,
                                        String idempotencyKey) {
        long startTime = System.nanoTime();
        if (amountMinorUnits <= 0) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, "Payment amount must be greater than zero minor units");
        }

        // 1. Acquire pessimistic lock on customer wallet to prevent concurrent double-spending
        Wallet wallet = walletRepository.findForUpdateByIdAndMerchantId(customerWalletId, merchant.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Customer wallet not found: " + customerWalletId));

        if (wallet.getCurrency() != currency) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_CURRENCY,
                    String.format("Wallet currency [%s] does not match payment currency [%s]", wallet.getCurrency(), currency));
        }

        Account merchantSettlementAccount = ledgerService.getOrCreateMerchantSettlementAccount(merchant, currency);

        // 2. Check sufficient balance
        if (wallet.getBalanceMinorUnits() < amountMinorUnits) {
            log.warn("Payment declined: Insufficient funds for wallet [{}]. Available: {}, Requested: {}",
                    wallet.getId(), wallet.getBalanceMinorUnits(), amountMinorUnits);

            Payment failedPayment = new Payment(
                    merchant,
                    wallet,
                    merchantSettlementAccount,
                    amountMinorUnits,
                    currency,
                    idempotencyKey
            );
            failedPayment.setStatus(PaymentStatus.FAILED);
            failedPayment.setFailureReason("Insufficient wallet balance");
            Payment savedFailed = paymentRepository.save(failedPayment);

            recordOutboxEvent("PAYMENT", savedFailed.getId().toString(), "payment.failed", savedFailed);

            if (metrics != null) {
                metrics.recordPaymentFailure(currency.name(), "INSUFFICIENT_FUNDS", System.nanoTime() - startTime);
            }

            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS,
                    String.format("Customer wallet [%s] has available balance %s, but required %s",
                            wallet.getId(),
                            currency.formatMinorUnits(wallet.getBalanceMinorUnits()),
                            currency.formatMinorUnits(amountMinorUnits)));
        }

        // 3. Post double-entry journal (DR: Customer Asset, CR: Merchant Liability)
        String paymentRef = "pay_" + UUID.randomUUID();
        String journalDesc = (description != null && !description.isBlank()) ? description : "Payment charge";

        JournalEntry journal = ledgerService.postJournal(
                paymentRef,
                ReferenceType.PAYMENT,
                journalDesc,
                List.of(
                        new LedgerService.PostingInstruction(merchantSettlementAccount, JournalDirection.DEBIT, amountMinorUnits, currency),
                        new LedgerService.PostingInstruction(wallet.getAccount(), JournalDirection.CREDIT, amountMinorUnits, currency)
                )
        );

        // 4. Atomically deduct wallet balance
        long newBalance = wallet.getBalanceMinorUnits() - amountMinorUnits;
        wallet.setBalanceMinorUnits(newBalance);
        walletRepository.save(wallet);

        // 5. Create and capture payment
        Payment payment = new Payment(
                merchant,
                wallet,
                merchantSettlementAccount,
                amountMinorUnits,
                currency,
                idempotencyKey
        );
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setJournalEntry(journal);
        Payment savedPayment = paymentRepository.save(payment);

        // 6. Record outbox event for Kafka publishing
        recordOutboxEvent("PAYMENT", savedPayment.getId().toString(), "payment.captured", savedPayment);

        if (metrics != null) {
            metrics.recordPaymentSuccess(currency.name(), System.nanoTime() - startTime);
        }

        log.info("Payment captured: [{}] amount: {} {}. Remaining wallet balance: {}. Journal: [{}]",
                savedPayment.getId(), amountMinorUnits, currency, newBalance, journal.getId());

        return toPaymentResult(savedPayment);
    }

    @Transactional(readOnly = true)
    public PaymentResult getPayment(UUID id, UUID merchantId) {
        Payment payment = paymentRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Payment not found: " + id));
        return toPaymentResult(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResult> listPayments(UUID merchantId) {
        return paymentRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                .map(this::toPaymentResult)
                .toList();
    }

    private PaymentResult toPaymentResult(Payment payment) {
        return new PaymentResult(
                payment.getId(),
                payment.getCustomerWallet().getCustomerId(),
                payment.getCustomerWallet().getId(),
                payment.getAmountMinorUnits(),
                payment.getCurrency().formatMinorUnits(payment.getAmountMinorUnits()),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getFailureReason(),
                payment.getIdempotencyKey(),
                payment.getJournalEntry() != null ? payment.getJournalEntry().getId() : null,
                payment.getCreatedAt()
        );
    }

    private void recordOutboxEvent(String aggregateType, String aggregateId, String eventType, Payment payment) {
        try {
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("eventId", UUID.randomUUID().toString());
            payloadMap.put("paymentId", payment.getId().toString());
            payloadMap.put("merchantId", payment.getMerchant().getId().toString());
            payloadMap.put("customerWalletId", payment.getCustomerWallet().getId().toString());
            payloadMap.put("amountMinorUnits", payment.getAmountMinorUnits());
            payloadMap.put("currency", payment.getCurrency().name());
            payloadMap.put("status", payment.getStatus().name());
            payloadMap.put("idempotencyKey", payment.getIdempotencyKey());
            payloadMap.put("journalEntryId", payment.getJournalEntry() != null ? payment.getJournalEntry().getId().toString() : null);
            payloadMap.put("timestamp", Instant.now().toString());

            String jsonPayload = objectMapper.writeValueAsString(payloadMap);
            outboxEventRepository.save(new OutboxEvent(aggregateType, aggregateId, eventType, jsonPayload));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event for payment [{}]", payment.getId(), e);
        }
    }
}
