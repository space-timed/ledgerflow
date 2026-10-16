# LedgerFlow (Payment Engine) — Implementation Plan & Progress Tracker

## Status: IN PROGRESS (PHASE 2: Merchant, Wallet and Ledger Accounting)
**Target Repository:** `https://github.com/space-timed/payment-engine`  
**Root Directory:** `C:\agy\sideprojects_for_resume\payment-engine`  
**Active Stack:** Java 26 (targeting Java 21 bytecode), Spring Boot 3.4.x, PostgreSQL 16, Apache Kafka 4.3.1, Redis 7 (optional cache), Flyway, Maven, Docker Compose, React + TypeScript + Vite.

---

## Phase Roadmap & Checklists

### PHASE 0 — Discovery and Architecture [COMPLETED]
- [x] Environment discovery (OS, CPU, RAM, Disk, Java, Maven, Docker, WSL, Git, GH CLI, Node, npm, PostgreSQL)
- [x] Identify installed vs. missing dependencies
- [x] Formulate zero-administrative footprint local resource plan
- [x] Draft architectural specifications (`docs/ARCHITECTURE.md`)
- [x] Draft database design & double-entry schema (`docs/DATABASE_DESIGN.md`)
- [x] Draft API contract & idempotency specification (`docs/API_DESIGN.md`)
- [x] Document key Architectural Decision Records (`docs/DECISIONS.md`)
- [x] Submit Phase 0 Report and Dependency Download Approval Request to user
- [x] Receive user approval to begin Phase 1 dependency resolution and bootstrapping

### PHASE 1 — Project Foundation [COMPLETED]
- [x] Initialize Git repository in root with proper `.gitignore` and `.gitattributes`
- [x] Configure root Maven `pom.xml` with Spring Boot 3.4.x, Spring Data JPA, Flyway, PostgreSQL, Validation, Actuator, Springdoc OpenAPI
- [x] Configure `compose.yaml` with profiles (`core`, `events`, `observability`, `full`)
- [x] Define PostgreSQL dedicated container on non-conflicting port (5433:5432)
- [x] Implement database migration baseline `V1__init_schema.sql`
- [x] Configure application properties (`application.yml`, `application-dev.yml`, `application-test.yml`)
- [x] Setup structured logging (SLF4J / Logback) with Correlation ID (`X-Correlation-Id`)
- [x] Implement RFC 7807 Global Exception Handler and Problem Details
- [x] Implement Health Check & OpenAPI Swagger UI configuration
- [x] Establish JUnit 5 test harness
- [x] Create single documented startup scripts (`scripts/dev-up.ps1` / `scripts/dev-down.ps1`)
- [x] Phase Exit Verification: Green integration smoke test connecting to live PostgreSQL container and health check verification

### PHASE 2 — Merchant, Wallet and Ledger Accounting [COMPLETED]
- [x] Domain Module: `merchant` (Merchant entity, API Key generation & SHA-256 hashing, API Key Auth filter, multi-tenant isolation)
- [x] Domain Module: `ledger` (Immutable Accounts, Journals, Journal Lines, double-entry verification engine)
- [x] Domain Module: `wallet` (Customer wallets, currency validation, balance query, deterministic balance computation)
- [x] Sandbox Funding Service (`POST /api/v1/sandbox/funding`) via double-entry journal from system clearing account
- [x] Balance invariant enforcement: sum(debits) == sum(credits) in every journal entry
- [x] Unit & Integration tests for:
  - Wallet creation & merchant scoping
  - Sandbox funding
  - Balanced postings validation
  - Currency exponent & ISO validation (minor units: paise, cents)
  - Negative/zero amount rejection
  - Transaction rollback on unbalanced journals
- [x] Phase Exit Verification: Tested immutable double-entry ledger with merchant isolation (8/8 tests passing)

### PHASE 3 — Payment Processing and Refunds [CURRENT]
- [ ] Domain Module: `payment` (Payment entity, status lifecycle machine: `CREATED`, `CAPTURED`, `FAILED`, `PARTIALLY_REFUNDED`, `REFUNDED`)
- [ ] Payment Intent & Capture workflow (`POST /api/v1/payments`, `POST /api/v1/payments/{id}/capture`)
- [ ] Concurrency Control: PostgreSQL row-level locking (`SELECT ... FOR UPDATE`) with deterministic account ID ordering
- [ ] Idempotency Engine (`Idempotency-Key` header with SHA-256 payload fingerprinting and cached atomic response)
- [ ] Deterministic failure simulation (insufficient funds, simulated decline, duplicate key mismatch)
- [ ] Domain Module: `refund` (Full & partial refunds, cumulative refund limits, ledger reversal postings)
- [ ] Multi-threaded concurrent payment safety tests (simultaneous wallet balance depletion)
- [ ] Idempotency recovery tests across retries
- [ ] Phase Exit Verification: Concurrency-safe, idempotent payment and refund operations with zero invariant violations

### PHASE 4 — Kafka, Outbox and Reliable Event Delivery
- [ ] Domain Module: `outbox` (Transactional Outbox table, atomic DB persistence with business transaction)
- [ ] Kafka cluster integration (`compose.yaml` with Kafka KRaft mode container)
- [ ] Background Outbox Poller & Publisher with safe row claiming (`SKIP LOCKED`) and exponential backoff
- [ ] Event Schemas: `payment.created`, `payment.captured`, `payment.failed`, `payment.refunded`, `wallet.funded`
- [ ] Idempotent Kafka Consumer base with consumer deduplication table (`consumed_events`)
- [ ] Dead-Letter Topic (DLT) routing and poison message isolation
- [ ] Resilience tests:
  - Payment commits when Kafka broker is down (stored safely in outbox)
  - Automatic drain of outbox once Kafka recovers
  - Duplicate event delivery tolerance (at-least-once -> exactly-once business effect)
- [ ] Phase Exit Verification: Durable, observable, and crash-resilient event pipeline

### PHASE 5 — Webhooks and Reconciliation Engine
- [ ] Domain Module: `webhook` (Subscription management, endpoint verification, HMAC-SHA256 payload signature)
- [ ] Webhook dispatcher worker (async delivery, HTTP timeouts, exponential backoff with jitter, retry tracking)
- [ ] Manual webhook redelivery endpoint (`POST /api/v1/webhooks/deliveries/{id}/redeliver`)
- [ ] SSRF Protection (strict allowlisting of webhook destination IP/hosts, local receiver only)
- [ ] Mock Webhook Receiver endpoint (`/api/v1/mock/webhook-receiver`) for automated demo & verification
- [ ] Domain Module: `reconciliation` (Independent auditor verifying ledger debits vs credits, wallet balances vs journal sum, payment totals vs ledger)
- [ ] Controlled fault-injection test fixture to verify discrepancy detection
- [ ] Reconciliation reporting API (`POST /api/v1/reconciliation/run`, `GET /api/v1/reconciliation/runs/{id}`)
- [ ] Phase Exit Verification: Verified HMAC webhook delivery and automated financial reconciliation auditor

### PHASE 6 — Operational Dashboard (Frontend)
- [ ] Scaffold React + TypeScript + Vite dashboard in `frontend/`
- [ ] Clean, high-density GitHub-dark aesthetic (Tailwind CSS or minimal CSS modules, Lucide icons)
- [ ] Views:
  - **Overview**: System throughput, payment status distribution, total processed volume by currency
  - **Payments**: Searchable/filterable transactions, lifecycle badge, linked ledger journal modal
  - **Wallets & Ledger Explorer**: Inspect balance breakdown, immutable double-entry journal postings
  - **Event & Outbox Monitor**: Kafka topics, outbox backlog, failed message inspection
  - **Webhook Deliveries**: Attempt log, signature status, one-click manual redeliver
  - **Reconciliation**: Audit history, invariant status, discrepancy alerts
- [ ] Mock sandbox simulator panel: Trigger test payments, refunds, and simulated failures directly from UI
- [ ] Phase Exit Verification: Responsive, real-data dashboard ready for recruiter evaluation and screenshots

### PHASE 7 — Observability, Security and Performance
- [ ] Micrometer & Prometheus metrics instrumentation (payments/sec, p50/p95/p99 latency, outbox backlog, ledger invariants)
- [ ] Docker Compose profile `observability` (Prometheus + Grafana with pre-configured dashboard JSON)
- [ ] Security audit: API key hashing, input sanitization, safe error masks, CORS, non-root container users
- [ ] Performance Load Harness (`scripts/load-test.ps1` or standalone Java harness)
- [ ] Execute realistic bounded benchmarks:
  - 10 concurrent payments
  - 50 concurrent payments
  - 100 concurrent payments competing for identical wallet funds
  - Measure p50, p95, p99, throughput, zero double-spend verification
- [ ] Document actual metrics in `docs/PERFORMANCE.md`
- [ ] Phase Exit Verification: Measured throughput, latency percentiles, and zero race conditions under load

### PHASE 8 — End-to-End Verification & GitHub Publication
- [ ] End-to-end automated demo script (`scripts/run-demo.ps1` / `scripts/run-demo.sh`) executing the complete 15-step scenario
- [ ] Full clean-slate verification (clean Docker compose, clean Flyway migrations, full test suite pass)
- [ ] Verification report generated (`docs/TESTING.md`)
- [ ] High-impact, recruiter-focused `README.md` (concise 120-150 lines, architecture diagram, flow, quick start)
- [ ] Commit history audit (clean atomic commits, no secrets, no binary clutter)
- [ ] Present publication report & request user final approval before pushing to `https://github.com/space-timed/payment-engine`
- [ ] Create repository via `gh` CLI, push `main`, and verify GitHub rendering
