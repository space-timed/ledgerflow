# LedgerFlow — System Architecture & Design

## 1. Executive Architecture Summary

LedgerFlow is designed as a **Modular Monolith with Asynchronous Event Workers**. It models the core architectural guarantees required of modern financial infrastructure:
- **Absolute Financial Correctness**: Double-entry ledger with immutable journals, minor currency units, and zero floating-point arithmetic.
- **Double-Spend Prevention**: Pessimistic row-level locking (`SELECT ... FOR UPDATE`) in PostgreSQL with strictly ordered lock acquisitions to eliminate deadlocks.
- **Strict Idempotency**: Atomic reservation of idempotency keys with SHA-256 payload fingerprinting and deterministic cached replay.
- **Transactional Outbox**: Guaranteed atomic persistence of business state and outbox events in a single PostgreSQL transaction, published asynchronously to Apache Kafka.
- **Event Deduplication**: Consumer-side idempotency guaranteeing that at-least-once delivery produces exactly-once financial effects.

---

## 2. High-Level Component Architecture

```mermaid
flowchart TD
    Client["Client / Merchant Application / Frontend"]
    
    subgraph SpringBootApp["LedgerFlow Modular Monolith (Spring Boot 3.4)"]
        direction TB
        
        subgraph WebLayer["API & Ingress"]
            Auth["API Key Auth & Rate Limiting"]
            Idemp["Idempotency Filter (SHA-256)"]
        end
        
        subgraph DomainModules["Domain Modules"]
            MerchantMod["Merchant & API Key Domain"]
            WalletMod["Wallet & Account Domain"]
            PaymentMod["Payment Processing Engine"]
            RefundMod["Refund Management Engine"]
            LedgerMod["Double-Entry Ledger Engine"]
            ReconMod["Reconciliation & Invariant Auditor"]
        end
        
        subgraph AsyncInfrastructure["Asynchronous Infrastructure"]
            OutboxWorker["Transactional Outbox Poller / Publisher"]
            KafkaConsumer["Idempotent Kafka Consumer"]
            WebhookDispatcher["Signed Webhook Dispatcher (HMAC-SHA256)"]
        end
    end

    subgraph Storage["Persistence & Messaging"]
        Postgres[(PostgreSQL 16\nSystem of Record)]
        Kafka[(Apache Kafka 4.3.1\nKRaft Mode)]
    end

    subgraph External["External Webhook Receivers"]
        MerchantEndpoint["Merchant Webhook URL"]
        MockReceiver["Mock Webhook Ingress (Test Harness)"]
    end

    Client -->|HTTPS REST + Idempotency-Key| Auth
    Auth --> Idemp
    Idemp --> PaymentMod
    Idemp --> RefundMod
    Idemp --> WalletMod

    PaymentMod --> LedgerMod
    RefundMod --> LedgerMod
    WalletMod --> LedgerMod

    PaymentMod -->|Atomic Transaction| Postgres
    LedgerMod -->|Journal Postings| Postgres
    PaymentMod -->|Outbox Event| Postgres

    OutboxWorker -->|Read SKIP LOCKED| Postgres
    OutboxWorker -->|Publish Event| Kafka

    Kafka -->|Consume Event| KafkaConsumer
    KafkaConsumer -->|Record Delivery / Trigger Action| Postgres
    KafkaConsumer --> WebhookDispatcher

    WebhookDispatcher -->|HTTP POST with HMAC| MerchantEndpoint
    WebhookDispatcher -->|HTTP POST with HMAC| MockReceiver

    ReconMod -->|Audit Invariants| Postgres
```

---

## 3. Package & Module Boundaries

The backend application is structured by business capability rather than technical layering:

```
com.ledgerflow.engine
├── common
│   ├── config          # Jackson, Async, Security, OpenAPI
│   ├── error           # RFC 7807 ProblemDetail exceptions & handler
│   ├── logging         # MDC Correlation ID filters and structured formatters
│   └── money           # CurrencyCode, MinorUnitMoney, Exponent rules
├── merchant
│   ├── domain          # Merchant, ApiKey entities
│   ├── repository      # Spring Data Repositories
│   └── service         # Merchant onboarding, key hashing & validation
├── wallet
│   ├── domain          # Wallet, BalanceSnapshot entities
│   ├── repository      # Wallet repository
│   └── service         # Wallet creation, balance inquiries, funding
├── ledger
│   ├── domain          # Account, JournalEntry, JournalLine, AccountType
│   ├── repository      # Ledger repositories
│   └── service         # Postings engine, sum(debits)==sum(credits) invariant
├── payment
│   ├── domain          # Payment, PaymentStatus, IdempotencyRecord
│   ├── repository      # Payment & Idempotency repositories
│   └── service         # Payment state machine, capture, concurrency locks
├── refund
│   ├── domain          # Refund, RefundStatus
│   ├── repository      # Refund repository
│   └── service         # Full/partial refunds, cumulative limit validation
├── outbox
│   ├── domain          # OutboxEvent, OutboxStatus
│   ├── repository      # Outbox repository (SELECT FOR UPDATE SKIP LOCKED)
│   └── service         # Transactional event persistence and Kafka publisher
├── event
│   ├── model           # Canonical domain event schemas (v1)
│   └── consumer        # Idempotent Kafka consumer listeners
├── webhook
│   ├── domain          # WebhookSubscription, WebhookDeliveryAttempt
│   ├── repository      # Webhook repositories
│   └── service         # HMAC-SHA256 signing, exponential backoff dispatcher
└── reconciliation
    ├── domain          # ReconciliationRun, ReconciliationDiscrepancy
    ├── repository      # Reconciliation repositories
    └── service         # Independent ledger & wallet balance auditor
```

---

## 4. End-to-End Payment Flow & Invariant Enforcement

```mermaid
sequenceDiagram
    autonumber
    actor Merchant as Client / Merchant
    participant API as Payment Controller
    participant Idemp as Idempotency Engine
    participant PaySvc as Payment Service
    participant WalletSvc as Wallet Service
    participant LedgerSvc as Double-Entry Ledger
    participant DB as PostgreSQL (System of Record)
    participant Outbox as Outbox Table
    participant Kafka as Apache Kafka Broker

    Merchant->>API: POST /api/v1/payments (Idempotency-Key: k_123, 5000 INR)
    API->>Idemp: Validate & lock Idempotency-Key
    alt Duplicate Request with Identical Key & Payload
        Idemp-->>Merchant: Return Cached Prior Response (HTTP 200/201)
    else Key Mismatch (Payload Diverged)
        Idemp-->>Merchant: 409 Conflict (Idempotency payload mismatch)
    end

    API->>PaySvc: Process Payment Intent
    Note over PaySvc,DB: Begin PostgreSQL Transaction (@Transactional SERIALIZABLE / READ COMMITTED)

    PaySvc->>WalletSvc: Verify Customer Wallet
    WalletSvc->>DB: SELECT * FROM wallets WHERE id = ? FOR UPDATE
    Note over WalletSvc,DB: Row-level lock acquired: balance cannot change concurrently

    PaySvc->>LedgerSvc: Create Journal Entry (Debit Customer, Credit Merchant)
    LedgerSvc->>LedgerSvc: Assert: sum(debits) == sum(credits)
    LedgerSvc->>DB: INSERT into journal_entries, journal_lines
    PaySvc->>DB: UPDATE wallet balances
    PaySvc->>DB: INSERT into payments (status = CAPTURED)
    PaySvc->>Outbox: INSERT into outbox_events (event = payment.captured)
    PaySvc->>Idemp: Store response payload in idempotency_records

    Note over PaySvc,DB: Commit PostgreSQL Transaction
    DB-->>PaySvc: Transaction Committed

    PaySvc-->>Merchant: HTTP 201 Created (Payment Details)

    par Background Event Publishing
        Outbox->>Kafka: Poller polls outbox (SKIP LOCKED) and publishes to 'payment.events'
        Kafka-->>Outbox: ACK received -> Mark Outbox status = PUBLISHED
    end
```

---

## 5. Payment State Machine

```mermaid
stateDiagram-v2
    [*] --> CREATED: Payment Initiated
    
    CREATED --> CAPTURED: Funds Verified & Journal Posted
    CREATED --> FAILED: Insufficient Funds / Decline / Invariant Error
    
    CAPTURED --> PARTIALLY_REFUNDED: Refund Amount < Total Captured
    PARTIALLY_REFUNDED --> PARTIALLY_REFUNDED: Additional Partial Refund
    PARTIALLY_REFUNDED --> REFUNDED: Cumulative Refunds == Total Captured
    CAPTURED --> REFUNDED: Full Refund Executed
    
    FAILED --> [*]
    REFUNDED --> [*]
```

---

## 6. Double-Entry Accounting Model

Every financial movement involves at least two balanced postings:

| Transaction Type | Debit Account (DR) | Credit Account (CR) | Invariant |
|---|---|---|---|
| **Sandbox Funding** | `ASSET:CUSTOMER_WALLET` | `EQUITY:SANDBOX_CLEARING` | DR = CR |
| **Payment Capture** | `LIABILITY:MERCHANT_PAYABLE` | `ASSET:CUSTOMER_WALLET` | DR = CR |
| **Refund Reversal** | `ASSET:CUSTOMER_WALLET` | `LIABILITY:MERCHANT_PAYABLE` | DR = CR |
| **Platform Fee (if configured)** | `REVENUE:PLATFORM_FEES` | `ASSET:CUSTOMER_WALLET` | DR = CR |

**Zero Floating Point Guarantee**: All amounts are recorded as `BIGINT` minor units (e.g., `10000` for `₹100.00` or `$100.00` where exponent is 2). Supported initial currencies: `INR` (Indian Rupee), `USD` (US Dollar), `EUR` (Euro), `GBP` (British Pound).

---

## 7. Fault Tolerance & Failure Recovery

1. **Kafka Broker Outage**:
   - The payment processing transaction commits to PostgreSQL regardless of Kafka broker availability.
   - Events reside durably in `outbox_events` with status `PENDING`.
   - Once Kafka recovers, the background poller drains the pending events with zero loss.
2. **Publisher Crash Post-Kafka ACK**:
   - If the outbox worker publishes an event to Kafka but crashes before updating the PostgreSQL outbox row, the event will be re-sent upon worker restart.
   - **Mitigation**: Kafka consumers use the event's unique `event_id` and consumer deduplication table (`consumed_events`) with an atomic `INSERT ON CONFLICT DO NOTHING` check to discard duplicates.
3. **Concurrent Competing Payments**:
   - When 10 threads attempt to drain a customer's ₹100 balance simultaneously:
   - Row-level lock `SELECT ... FOR UPDATE` serializes the balance check and journal posting.
   - The first transaction succeeds; subsequent transactions detect insufficient funds and gracefully fail without overdrafts.
