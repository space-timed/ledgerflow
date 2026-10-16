# Testing Strategy & Verification Report

This document details the testing architecture, invariant verification mechanisms, and test execution matrix for the **LedgerFlow Distributed Payment Engine**.

---

## 1. Test Architecture & Principles

The test suite is designed around financial correctness, concurrency safety, and crash resilience:

```
                  ┌────────────────────────────────────────┐
                  │ 15-Step E2E Demo (run-demo.ps1)        │
                  ├────────────────────────────────────────┤
                  │ Concurrency Benchmarks (10, 50, 100)   │
                  ├────────────────────────────────────────┤
                  │ Module Integration Tests (H2/Postgres) │
                  ├────────────────────────────────────────┤
                  │ Double-Entry Invariant Unit Tests      │
                  └────────────────────────────────────────┘
```

1. **Zero Financial Drift:** Every balance alteration is backed by an invariant check verifying $\sum \text{Debits} == \sum \text{Credits}$.
2. **Pessimistic Concurrency Safety:** Heavy multi-threaded competition tests ensure zero negative balances, zero overdrafts, and zero race conditions under lock contention.
3. **Idempotency Verification:** Identical payloads with identical idempotency keys return bit-for-bit cached responses without re-executing transactions.
4. **Resilience & Outbox Drain:** Event pipeline tests verify transactional outbox staging when Kafka is unavailable and automatic drain upon recovery.
5. **HMAC Webhook Cryptography:** Constant-time comparison tests prevent timing attacks on webhook signatures.

---

## 2. Test Execution Suite (19 / 19 Passing)

The full test suite executes via `mvn test` in ~23 seconds:

| Test Class | Focus Area | Tests | Key Assertion / Invariant | Result |
| :--- | :--- | :---: | :--- | :---: |
| `LedgerServiceDoubleEntryInvariantTest` | Core Ledger | 4 | Rejects imbalanced journals; enforces Debits == Credits | **PASS** |
| `FlywayMigrationPostgresIntegrationTest` | Persistence | 1 | Clean execution of 15 relational tables against PostgreSQL 16 | **PASS** |
| `MerchantAndWalletIntegrationTest` | Identity & Wallets | 1 | SHA-256 API key hashing & customer wallet balance isolation | **PASS** |
| `PaymentAndRefundIntegrationTest` | Payments & Refunds | 1 | Atomic wallet deduction, payment capture, and partial refund bounds | **PASS** |
| `ConcurrentPaymentSafetyIntegrationTest` | Concurrency Safety | 1 | 10 concurrent threads compete for 5,000 INR; exactly 5 succeed, 0 overdraft | **PASS** |
| `OutboxPublisherAndResilienceTest` | Transactional Outbox | 3 | Atomic outbox insertion; SKIP LOCKED poller drain; failure backoff | **PASS** |
| `ConsumerIdempotencyIntegrationTest` | Kafka Consumers | 1 | Duplicate Kafka event delivery yields single business execution | **PASS** |
| `WebhookSigningAndDeliveryIntegrationTest`| Webhooks | 2 | Timestamped HMAC-SHA256 signature generation and constant-time verify | **PASS** |
| `ReconciliationAuditIntegrationTest` | Financial Audit | 2 | Audits all journals and wallets; successfully detects injected anomaly | **PASS** |
| `BenchmarkLoadHarnessTest` | Load & Latency | 1 | Evaluates 10, 50, and 100 concurrent competing payments with p50/p95/p99 | **PASS** |
| `PaymentEngineApplicationTests` | Smoke & Context | 2 | Spring Boot application context loads with all actuators active | **PASS** |

**Total Suite Statistics:**
- **Tests Run:** 19
- **Failures:** 0
- **Errors:** 0
- **Skipped:** 0
- **Pass Rate:** 100.0%

---

## 3. Running the Tests

### Fast Test Suite Run
```powershell
mvn test
```

### Run Specific Test Focus
```powershell
# Core accounting invariants
mvn test -Dtest=LedgerServiceDoubleEntryInvariantTest

# Concurrency safety & zero overdraft under lock contention
mvn test -Dtest=ConcurrentPaymentSafetyIntegrationTest

# Webhook HMAC cryptographic signing
mvn test -Dtest=WebhookSigningAndDeliveryIntegrationTest

# Independent financial reconciliation audit
mvn test -Dtest=ReconciliationAuditIntegrationTest

# Concurrency load benchmark
mvn test -Dtest=BenchmarkLoadHarnessTest
```

### Full End-to-End Showcase
```powershell
# 1. Start Docker dependencies
powershell -ExecutionPolicy Bypass -File scripts/dev-up.ps1

# 2. Run Spring Boot application
mvn spring-boot:run

# 3. In another terminal, execute the 15-step automated demo
powershell -ExecutionPolicy Bypass -File scripts/run-demo.ps1
```
