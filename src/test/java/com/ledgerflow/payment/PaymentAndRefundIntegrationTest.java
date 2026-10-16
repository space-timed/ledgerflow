package com.ledgerflow.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.common.error.BusinessException;
import com.ledgerflow.common.error.ErrorCode;
import com.ledgerflow.common.money.CurrencyCode;
import com.ledgerflow.ledger.domain.JournalEntry;
import com.ledgerflow.ledger.domain.JournalLine;
import com.ledgerflow.ledger.service.LedgerService;
import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.merchant.service.MerchantService;
import com.ledgerflow.outbox.domain.OutboxEvent;
import com.ledgerflow.outbox.repository.OutboxEventRepository;
import com.ledgerflow.payment.domain.Payment;
import com.ledgerflow.payment.domain.PaymentStatus;
import com.ledgerflow.payment.repository.PaymentRepository;
import com.ledgerflow.payment.service.IdempotencyService;
import com.ledgerflow.payment.service.PaymentService;
import com.ledgerflow.refund.domain.Refund;
import com.ledgerflow.refund.repository.RefundRepository;
import com.ledgerflow.refund.service.RefundService;
import com.ledgerflow.wallet.domain.Wallet;
import com.ledgerflow.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentAndRefundIntegrationTest {

    @Autowired
    private MerchantService merchantService;
    @Autowired
    private WalletService walletService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private RefundService refundService;
    @Autowired
    private IdempotencyService idempotencyService;
    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private RefundRepository refundRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private Merchant merchant;
    private Wallet customerWallet;

    @BeforeEach
    void setUp() {
        MerchantService.MerchantRegistrationResult reg = merchantService.registerMerchant(
                "Acme Payments Inc",
                "finance-" + System.currentTimeMillis() + "@acme.com"
        );
        merchant = reg.merchant();

        customerWallet = walletService.createWallet(merchant, "cust_buyer_01", CurrencyCode.INR);

        // Fund with 10,000 minor units (₹100.00)
        walletService.fundSandboxWallet(
                customerWallet.getId(),
                merchant.getId(),
                10000,
                CurrencyCode.INR,
                "Initial test wallet deposit",
                "fund_key_" + System.currentTimeMillis()
        );
    }

    @Test
    @DisplayName("Complete Payment Lifecycle: Capture, Insufficient Funds, Idempotency, and Refunds")
    void testPaymentAndRefundLifecycle() {
        // 1. Process successful payment of 4,000 minor units (₹40.00)
        PaymentService.PaymentResult payment1 = paymentService.processPayment(
                merchant,
                customerWallet.getId(),
                4000,
                CurrencyCode.INR,
                "Purchase item A",
                "idem_pay_001"
        );

        assertNotNull(payment1.id());
        assertEquals(PaymentStatus.CAPTURED, payment1.status());
        assertEquals(4000, payment1.amountMinorUnits());
        assertEquals("₹ 40.00", payment1.formattedAmount());

        // Verify wallet balance reduced to 6,000 INR
        WalletService.WalletBalanceResponse balanceAfterPay1 = walletService.getWalletBalance(customerWallet.getId(), merchant.getId());
        assertEquals(6000, balanceAfterPay1.balanceMinorUnits());
        assertTrue(balanceAfterPay1.ledgerVerified());

        // Verify double-entry journal balance
        JournalEntry journal = ledgerService.getJournalEntry(payment1.journalEntryId());
        assertNotNull(journal);
        assertEquals(2, journal.getLines().size());
        long debits = journal.getLines().stream().filter(l -> l.getDirection().name().equals("DEBIT")).mapToLong(JournalLine::getAmountMinorUnits).sum();
        long credits = journal.getLines().stream().filter(l -> l.getDirection().name().equals("CREDIT")).mapToLong(JournalLine::getAmountMinorUnits).sum();
        assertEquals(4000, debits);
        assertEquals(4000, credits);

        // 2. Attempt payment with Insufficient Funds (requests 7,000 INR when 6,000 INR remains)
        BusinessException nsfEx = assertThrows(BusinessException.class, () ->
                paymentService.processPayment(
                        merchant,
                        customerWallet.getId(),
                        7000,
                        CurrencyCode.INR,
                        "Expensive item",
                        "idem_pay_002"
                )
        );
        assertEquals(ErrorCode.INSUFFICIENT_FUNDS, nsfEx.getErrorCode());

        // Wallet balance must remain unaffected at 6,000 INR
        assertEquals(6000, walletService.getWalletBalance(customerWallet.getId(), merchant.getId()).balanceMinorUnits());

        // 3. Test Idempotency Engine
        String reqBody1 = "{\"walletId\":\"" + customerWallet.getId() + "\",\"amount\":4000}";
        String fp1 = IdempotencyService.computeRequestFingerprint("POST", "/api/v1/payments", reqBody1);

        // Reserve key
        Optional<IdempotencyService.CachedResponse> check1 = idempotencyService.checkOrReserveIdempotencyKey(merchant, "key_idem_test", fp1);
        assertTrue(check1.isEmpty(), "First request should proceed");

        // Record completion
        idempotencyService.recordCompletion(merchant, "key_idem_test", 201, "{\"id\":\"" + payment1.id() + "\"}");

        // Replay with identical fingerprint returns cached response
        Optional<IdempotencyService.CachedResponse> replay = idempotencyService.checkOrReserveIdempotencyKey(merchant, "key_idem_test", fp1);
        assertTrue(replay.isPresent());
        assertEquals(201, replay.get().statusCode());

        // Replay with mismatching fingerprint throws IDEMPOTENCY_PAYLOAD_MISMATCH
        String reqBodyModified = "{\"walletId\":\"" + customerWallet.getId() + "\",\"amount\":9999}";
        String fp2 = IdempotencyService.computeRequestFingerprint("POST", "/api/v1/payments", reqBodyModified);

        BusinessException mismatchEx = assertThrows(BusinessException.class, () ->
                idempotencyService.checkOrReserveIdempotencyKey(merchant, "key_idem_test", fp2)
        );
        assertEquals(ErrorCode.IDEMPOTENCY_PAYLOAD_MISMATCH, mismatchEx.getErrorCode());

        // 4. Partial Refund: 1,500 minor units on payment1
        RefundService.RefundResult refund1 = refundService.processRefund(
                merchant,
                payment1.id(),
                1500,
                "Partial return of item A",
                "idem_ref_001"
        );

        assertNotNull(refund1.refundId());
        assertEquals(1500, refund1.amountMinorUnits());
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, refund1.paymentStatus());
        assertEquals(2500, refund1.remainingRefundableMinorUnits());

        // Customer wallet balance increases from 6,000 to 7,500 INR
        assertEquals(7500, walletService.getWalletBalance(customerWallet.getId(), merchant.getId()).balanceMinorUnits());

        // 5. Complete Remaining Refund: 2,500 minor units
        RefundService.RefundResult refund2 = refundService.processRefund(
                merchant,
                payment1.id(),
                2500,
                "Return remainder of item A",
                "idem_ref_002"
        );

        assertEquals(2500, refund2.amountMinorUnits());
        assertEquals(PaymentStatus.REFUNDED, refund2.paymentStatus());
        assertEquals(0, refund2.remainingRefundableMinorUnits());

        // Customer wallet restored to full original balance: 10,000 INR
        assertEquals(10000, walletService.getWalletBalance(customerWallet.getId(), merchant.getId()).balanceMinorUnits());

        // 6. Excess Refund Rejection: Attempting another refund on already-refunded payment
        BusinessException excessEx = assertThrows(BusinessException.class, () ->
                refundService.processRefund(
                        merchant,
                        payment1.id(),
                        500,
                        "Excess refund attempt",
                        "idem_ref_003"
                )
        );
        assertEquals(ErrorCode.PAYMENT_NOT_IN_REFUNDABLE_STATE, excessEx.getErrorCode());
    }
}
