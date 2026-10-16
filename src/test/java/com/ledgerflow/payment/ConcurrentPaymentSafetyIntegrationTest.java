package com.ledgerflow.payment;

import com.ledgerflow.common.error.BusinessException;
import com.ledgerflow.common.money.CurrencyCode;
import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.merchant.service.MerchantService;
import com.ledgerflow.payment.domain.PaymentStatus;
import com.ledgerflow.payment.repository.PaymentRepository;
import com.ledgerflow.payment.service.PaymentService;
import com.ledgerflow.wallet.domain.Wallet;
import com.ledgerflow.wallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentPaymentSafetyIntegrationTest {

    @Autowired
    private MerchantService merchantService;
    @Autowired
    private WalletService walletService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("Concurrency Stress Test: 10 concurrent threads compete for 5,000 minor units; exactly 5 succeed, zero double-spending")
    void testConcurrentPaymentSafety() throws InterruptedException {
        // 1. Setup isolated merchant and customer wallet
        MerchantService.MerchantRegistrationResult reg = merchantService.registerMerchant(
                "Concurrent Stress Merchant",
                "stress-" + UUID.randomUUID() + "@merchant.com"
        );
        Merchant merchant = reg.merchant();

        Wallet wallet = walletService.createWallet(merchant, "cust_stress_01", CurrencyCode.INR);

        // 2. Fund with exactly 5,000 minor units (₹50.00)
        walletService.fundSandboxWallet(
                wallet.getId(),
                merchant.getId(),
                5000,
                CurrencyCode.INR,
                "Stress fund 5000",
                "fund_stress_" + UUID.randomUUID()
        );

        // 3. Launch 10 concurrent threads attempting to spend 1,000 minor units each
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientFundsCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Synchronized gate for simultaneous start

                    paymentService.processPayment(
                            merchant,
                            wallet.getId(),
                            1000,
                            CurrencyCode.INR,
                            "Concurrent payment #" + index,
                            "idem_stress_" + index + "_" + UUID.randomUUID()
                    );
                    successCount.incrementAndGet();
                } catch (BusinessException be) {
                    if (be.getErrorCode().name().equals("INSUFFICIENT_FUNDS")) {
                        insufficientFundsCount.incrementAndGet();
                    } else {
                        otherErrorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherErrorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Fire all 10 threads simultaneously
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All concurrent payment threads must finish within timeout");

        // 4. Invariant Assertions
        assertEquals(5, successCount.get(), "Exactly 5 payments must succeed for a 5,000 balance with 1,000 charges");
        assertEquals(5, insufficientFundsCount.get(), "Exactly 5 payments must fail with INSUFFICIENT_FUNDS");
        assertEquals(0, otherErrorCount.get(), "Zero unexpected exceptions or deadlock errors");

        // 5. Final Wallet Balance verification
        WalletService.WalletBalanceResponse finalBalance = walletService.getWalletBalance(wallet.getId(), merchant.getId());
        assertEquals(0, finalBalance.balanceMinorUnits(), "Wallet balance must be exactly 0, never negative (no overdraft)");
        assertTrue(finalBalance.ledgerVerified(), "Double-entry ledger balance must agree with wallet cached balance");
    }
}
