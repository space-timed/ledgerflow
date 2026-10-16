# Performance Benchmark & Concurrency Safety Report

This document records the empirical performance benchmarks, concurrency safety stress results, and latency percentiles measured on the **LedgerFlow Distributed Payment Engine**.

---

## 1. Test Methodology & Invariant Criteria

The benchmark evaluates the system under aggressive concurrent contention where multiple threads simultaneously attempt to capture payments from a single customer wallet with finite funding.

### Concurrency Stress Scenario
1. An isolated merchant is onboarded with a dedicated SHA-256 API key.
2. A customer wallet is created in `INR` and funded with a fixed pool:
   - For $N$ concurrent requests, the pool is intentionally sized to afford only $M < N$ payments.
   - The remaining $N - M$ requests **must** be deterministically declined with `INSUFFICIENT_FUNDS`.
3. An `ExecutorService` and `CountDownLatch` release all worker threads simultaneously.
4. Each thread records its end-to-end processing latency in nanoseconds.
5. Post-benchmark validation asserts:
   - Exactly $M$ payments transitioned to `CAPTURED`.
   - Exactly $N - M$ payments transitioned to `FAILED`.
   - The final cached wallet balance is exactly `0`.
   - The sum of ledger asset journal lines is exactly `0`.
   - Zero double-spending occurred.

---

## 2. Empirical Benchmark Results

The following numbers were measured directly using [`BenchmarkLoadHarnessTest.java`](../src/test/java/com/ledgerflow/performance/BenchmarkLoadHarnessTest.java):

| Benchmark Run | Concurrency (Threads) | Completed Payments | Declined Payments | Throughput (req/s) | p50 Latency (ms) | p95 Latency (ms) | p99 Latency (ms) | Invariant Violations |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **BENCHMARK-10** | 10 | 5 | 5 | **53.12** | 147.56 ms | 186.16 ms | 186.16 ms | **0 (Zero)** |
| **BENCHMARK-50** | 50 | 20 | 30 | **121.41** | 260.84 ms | 392.22 ms | 402.83 ms | **0 (Zero)** |
| **BENCHMARK-100** | 100 | 40 | 60 | **173.21** | 391.28 ms | 546.69 ms | 562.35 ms | **0 (Zero)** |

---

## 3. Analysis & Key Takeaways

### 1. Zero Overdraft & Invariant Integrity
Across all benchmark runs up to 100 concurrent competing threads, **zero race conditions or negative balances occurred**.
PostgreSQL row-level locking (`SELECT ... FOR UPDATE` via `WalletRepository.findForUpdateByIdAndMerchantId`) cleanly serializes competing balance deductions. Subsequent requests whose balance requirement exceeds available funds safely decline without corrupting the double-entry ledger.

### 2. Double-Entry Accounting Consistency
Every succeeded payment posted an immutable, balanced two-legged journal entry:
- `DEBIT Merchant Settlement Account`
- `CREDIT Customer Asset Wallet`
The independent ledger auditor confirmed $\sum \text{Debits} == \sum \text{Credits}$ with 100% balance equality.

### 3. Latency Distribution
- **p50 Latency:** Scales smoothly from 147 ms at 10 threads to 391 ms at 100 threads under intense single-row lock contention.
- **p95 Latency:** Remains bounded under 550 ms even when 100 threads contend for identical database row locks.
- **Throughput:** Climbs from 53 req/sec to 173 req/sec as connection pooling and thread worker utilization reach steady state.

### 4. Transactional Outbox Decoupling
Because payment capture commits synchronously to PostgreSQL and stages domain events in `outbox_events` within the identical database transaction, client payment latency is completely isolated from downstream Kafka broker network jitter or transient broker unavailability.

---

## 4. How to Reproduce

### In-Process Benchmark Suite
```powershell
mvn test -Dtest=BenchmarkLoadHarnessTest
```

### End-to-End HTTP Load Harness
With the Spring Boot server running on port 8080:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/load-test.ps1 -Concurrency 50
```
