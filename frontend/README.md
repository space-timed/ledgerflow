# LedgerFlow Operational Dashboard & Sandbox Simulator

A high-density FinTech operational dashboard built with **React 19**, **TypeScript**, and **Vite**, featuring Tailwind CSS dark theme design and Lucide icons.

The frontend is served directly by the Spring Boot backend at `http://localhost:8080/`, and can also be run independently in hot-reload mode during development.

---

## Capabilities

- **Overview & Telemetry:** Real-time throughput metrics, payment volume aggregation by currency, and payment state lifecycle breakdown.
- **Payment Explorer:** Full search and filtering across payments, lifecycle badges, and linked double-entry ledger modals.
- **Wallets & Double-Entry Ledger:** Real-time customer balance inspections and immutable balanced journal postings ($\sum \text{Debits} == \sum \text{Credits}$).
- **Outbox & Event Monitor:** Transactional outbox staging, publication status, and Kafka event logs.
- **Webhook Deliveries:** HMAC-SHA256 timestamped signatures, delivery attempt logs, and one-click redelivery console.
- **Reconciliation Auditor:** Real-time financial invariant auditing, discrepancy tracking, and anomaly verification.
- **Interactive Sandbox Simulator:** Trigger test payments, refunds, and simulated failure scenarios directly from the browser.

---

## Development Setup

```bash
# Install dependencies
npm install

# Start Vite dev server (proxies API requests to http://localhost:8080)
npm run dev

# Production build (bundles directly into src/main/resources/static/)
npm run build
```
