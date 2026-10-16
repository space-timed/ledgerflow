# Architecture Decision Records (ADRs) — LedgerFlow

## ADR-001: Modular Monolith vs Microservices Architecture
- **Status**: Accepted
- **Context**: Financial transaction engines require strong transactional consistency across accounts, ledgers, wallets, and outbox tables. Splitting the engine into independent microservices at this stage introduces distributed transactions (Saga / 2PC), network latency, partial failure states, and significant container overhead on a local laptop.
- **Decision**: Build LedgerFlow as a strictly bounded **Modular Monolith** running within a single Spring Boot runtime with asynchronous background workers.
- **Consequences**:
  - Eliminates distributed two-phase commit overhead; atomic PostgreSQL transactions guarantee that balance checks, ledger entries, and outbox rows commit or roll back together.
  - Domain packages (`merchant`, `wallet`, `ledger`, `payment`, `refund`, `outbox`, `webhook`, `reconciliation`) enforce clear public interfaces with zero circular dependencies, facilitating clean future extraction if scaling demands.

---

## ADR-002: PostgreSQL as Financial System of Record
- **Status**: Accepted
- **Context**: A payment engine cannot tolerate lost writes, dirty reads, or phantom state. In-memory stores like Redis cannot guarantee durability under sudden node failures.
- **Decision**: PostgreSQL 16 is designated the sole, authoritative **system of record**. Redis may only be used for non-critical transient caching or rate limiting, but never for balance calculations, ledger history, or idempotency state.
- **Consequences**:
  - Full ACID guarantees across all ledger postings.
  - Rich constraint enforcement (`CHECK (amount > 0)`, unique indexes).
  - Outbox queries leverage `FOR UPDATE SKIP LOCKED` for lock-free parallel event publication.

---

## ADR-003: Transactional Outbox for Reliable Messaging
- **Status**: Accepted
- **Context**: Publishing to a message broker (Kafka) directly within a database transaction risks two failure modes:
  1. Transaction rolls back after message is sent (ghost event published).
  2. Transaction commits but Kafka broker fails (lost event).
- **Decision**: Implement the **Transactional Outbox Pattern**. Business state and domain events are persisted in the same PostgreSQL transaction into an `outbox_events` table. A background poller reads pending events, publishes them to Kafka, and marks them `PUBLISHED` upon broker ACK.
- **Consequences**:
  - Guarantees at-least-once event delivery to Kafka.
  - Decouples API latency from Kafka broker availability. Payments succeed even if Kafka is temporarily down.

---

## ADR-004: Concurrency Control via Deterministic Row Locking
- **Status**: Accepted
- **Context**: Concurrent requests spending from the same customer wallet must not cause negative balances (double-spending) or deadlocks.
- **Decision**: Use PostgreSQL row-level pessimistic locking (`SELECT ... FOR UPDATE`) on wallet accounts. When a transaction requires locking multiple accounts (e.g. transfer between wallets), locks are acquired in ascending lexicographical order of account UUIDs.
- **Consequences**:
  - Strict serialization of competing balance mutations prevents race conditions and overdrafts.
  - Strict lock ordering mathematically eliminates database deadlocks.

---

## ADR-005: Idempotency Key Design with Request Fingerprinting
- **Status**: Accepted
- **Context**: Network timeouts often cause clients to retry payment requests. Without idempotency, duplicate charges occur.
- **Decision**: Financial mutation endpoints require an `Idempotency-Key` HTTP header. The engine computes a SHA-256 fingerprint of the normalized request (HTTP method + URI + request body). 
  - If a matching key with the same fingerprint exists and is completed, the cached response is immediately returned.
  - If a matching key with a different fingerprint is received, the request is rejected with `409 Conflict`.
  - An atomic insert with status `IN_PROGRESS` prevents concurrent race conditions on the same key.
- **Consequences**:
  - Safe automatic retries for clients.
  - Zero duplicate payments or ledger postings.

---

## ADR-006: Immutable Double-Entry Ledger with Minor Currency Units
- **Status**: Accepted
- **Context**: Traditional financial systems maintain an audit log where every transaction consists of balanced debits and credits. Floating point numbers lead to IEEE 754 precision errors.
- **Decision**: 
  - All financial amounts are stored as 64-bit integers (`BIGINT`) representing minor currency units (e.g. cents, paise).
  - The ledger enforces the invariant: $\sum \text{Debits} = \sum \text{Credits}$ for every posted journal.
  - Journal entries and lines are strictly immutable; corrections are executed exclusively via linked reversal journals.
- **Consequences**:
  - Absolute mathematical precision with zero rounding error.
  - Complete, tamper-evident audit trail for financial reconciliation.

---

## ADR-007: Docker Compose Profile Strategy for Resource Optimization
- **Status**: Accepted
- **Context**: The host machine is a laptop with 20 GB RAM and ~7.6 GB free physical memory. Running Kafka, Zookeeper, PostgreSQL, Redis, Prometheus, Grafana, and frontend simultaneously could stress resources.
- **Decision**: Structure `compose.yaml` with clean Docker Compose profiles:
  - `core`: PostgreSQL (dedicated port 5433 to avoid collision with existing 5432 container)
  - `events`: Apache Kafka (KRaft mode, avoiding separate Zookeeper container)
  - `observability`: Prometheus & Grafana
  - `full`: All services
- **Consequences**:
  - Minimal base memory footprint (~350 MB for core).
  - Rapid local startup without running unnecessary daemons.

---

## ADR-008: Adoption of Installed Java 26 Environment
- **Status**: Accepted
- **Context**: The developer environment has OpenJDK 26 (`C:\Installations\jdk26`) and Apache Maven 3.9.16 pre-installed.
- **Decision**: Leverage the existing OpenJDK 26 installation directly, configuring Maven and the compiler plugins to target Java 21 bytecode compatibility (`--release 21`).
- **Consequences**:
  - No need to download or install a separate JDK.
  - Maintains full compatibility with Spring Boot 3.4.x and standard JVM libraries.
