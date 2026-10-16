package com.ledgerflow.performance;

import com.ledgerflow.ledger.domain.AccountType;
import com.ledgerflow.common.money.CurrencyCode;
import com.ledgerflow.ledger.service.LedgerService;
import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.merchant.service.MerchantService;
import com.ledgerflow.payment.repository.PaymentRepository;
import com.ledgerflow.payment.service.PaymentService;
import com.ledgerflow.wallet.domain.Wallet;
import com.ledgerflow.wallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class BenchmarkLoadHarnessTest {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkLoadHarnessTest.class);

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private PaymentRepository paymentRepository;

    public record BenchmarkResult(
            int totalRequests,
            int successCount,
            int declinedCount,
            double throughputReqPerSec,
            double p50Ms,
            double p95Ms,
            double p99Ms,
            double maxMs,
            boolean doubleSpendViolations
    ) {}

    @Test
    @DisplayName("Execute bounded concurrency benchmarks: 10, 50, and 100 concurrent requests competing for identical funds")
    void executeConcurrencyBenchmarks() throws Exception {
        log.info("===============================================================================");
        log.info("STARTING REAL CONCURRENCY LOAD BENCHMARK HARNESS");
        log.info("===============================================================================");

        // Run Benchmark 1: 10 concurrent requests
        BenchmarkResult b1 = runBenchmark("BENCHMARK-10", 10, 5, 1000);
        assertFalse(b1.doubleSpendViolations());
        assertEquals(5, b1.successCount());
        assertEquals(5, b1.declinedCount());

        // Run Benchmark 2: 50 concurrent requests
        BenchmarkResult b2 = runBenchmark("BENCHMARK-50", 50, 20, 500);
        assertFalse(b2.doubleSpendViolations());
        assertEquals(20, b2.successCount());
        assertEquals(30, b2.declinedCount());

        // Run Benchmark 3: 100 concurrent requests
        BenchmarkResult b3 = runBenchmark("BENCHMARK-100", 100, 40, 250);
        assertFalse(b3.doubleSpendViolations());
        assertEquals(40, b3.successCount());
        assertEquals(60, b3.declinedCount());

        log.info("===============================================================================");
        log.info("ALL BENCHMARKS COMPLETED WITH ZERO INVARIANT VIOLATIONS");
        log.info("B1 (10  threads): p50 = {} ms, p95 = {} ms, p99 = {} ms, Throughput = {} req/sec",
                b1.p50Ms(), b1.p95Ms(), b1.p99Ms(), String.format("%.2f", b1.throughputReqPerSec()));
        log.info("B2 (50  threads): p50 = {} ms, p95 = {} ms, p99 = {} ms, Throughput = {} req/sec",
                b2.p50Ms(), b2.p95Ms(), b2.p99Ms(), String.format("%.2f", b2.throughputReqPerSec()));
        log.info("B3 (100 threads): p50 = {} ms, p95 = {} ms, p99 = {} ms, Throughput = {} req/sec",
                b3.p50Ms(), b3.p95Ms(), b3.p99Ms(), String.format("%.2f", b3.throughputReqPerSec()));
        log.info("===============================================================================");
    }

    private BenchmarkResult runBenchmark(String testName,
                                         int concurrency,
                                         int maxAffordablePayments,
                                         long amountPerPaymentMinorUnits) throws Exception {
        long initialFunds = maxAffordablePayments * amountPerPaymentMinorUnits;
        CurrencyCode currency = CurrencyCode.INR;

        MerchantService.MerchantRegistrationResult reg = merchantService.registerMerchant(
                testName + " Merchant",
                testName.toLowerCase() + "-" + UUID.randomUUID() + "@merchant.com"
        );
        Merchant merchant = reg.merchant();

        Wallet wallet = walletService.createWallet(merchant, "cust_" + testName, currency);
        walletService.fundSandboxWallet(wallet.getId(), merchant.getId(), initialFunds, currency, "Benchmark initial pool", "fnd_" + UUID.randomUUID());

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(concurrency);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger declinedCount = new AtomicInteger(0);
        List<Long> latenciesNanos = Collections.synchronizedList(new ArrayList<>());

        long benchmarkStartNanos = System.nanoTime();

        for (int i = 0; i < concurrency; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    long reqStart = System.nanoTime();
                    try {
                        paymentService.processPayment(
                                merchant,
                                wallet.getId(),
                                amountPerPaymentMinorUnits,
                                currency,
                                testName + " payment #" + index,
                                "idemp_" + testName + "_" + index
                        );
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        declinedCount.incrementAndGet();
                    } finally {
                        long reqDuration = System.nanoTime() - reqStart;
                        latenciesNanos.add(reqDuration);
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Release the latch simultaneously across all threads
        startGate.countDown();
        boolean completed = endGate.await(30, TimeUnit.SECONDS);
        long totalDurationNanos = System.nanoTime() - benchmarkStartNanos;
        executor.shutdown();

        assertTrue(completed, "Benchmark threads did not finish within timeout");

        // Verify balance and accounting invariants
        Wallet finalWallet = walletService.getWallet(wallet.getId(), merchant.getId());
        long ledgerBalance = ledgerService.calculateAccountBalance(finalWallet.getAccount().getId(), AccountType.ASSET);

        boolean invariantOk = (finalWallet.getBalanceMinorUnits() == 0) && (ledgerBalance == 0);

        // Calculate percentiles
        List<Long> sortedLatencies = new ArrayList<>(latenciesNanos);
        Collections.sort(sortedLatencies);

        double p50 = percentile(sortedLatencies, 0.50);
        double p95 = percentile(sortedLatencies, 0.95);
        double p99 = percentile(sortedLatencies, 0.99);
        double max = sortedLatencies.isEmpty() ? 0 : sortedLatencies.get(sortedLatencies.size() - 1) / 1_000_000.0;
        double throughput = (concurrency / (totalDurationNanos / 1_000_000_000.0));

        return new BenchmarkResult(
                concurrency,
                successCount.get(),
                declinedCount.get(),
                throughput,
                p50,
                p95,
                p99,
                max,
                !invariantOk
        );
    }

    private double percentile(List<Long> sortedNanos, double p) {
        if (sortedNanos.isEmpty()) return 0.0;
        int index = (int) Math.ceil(p * sortedNanos.size()) - 1;
        index = Math.max(0, Math.min(index, sortedNanos.size() - 1));
        return sortedNanos.get(index) / 1_000_000.0; // convert to ms
    }
}
