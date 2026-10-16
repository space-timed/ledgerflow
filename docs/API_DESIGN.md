# LedgerFlow — REST API Contract & Specification (v1)

## 1. Global API Conventions

- **Base URL**: `/api/v1`
- **Protocol**: HTTPS / JSON (`application/json`)
- **Authentication**: `X-Api-Key: lf_live_<secret_token>` header on all merchant-scoped endpoints.
- **Correlation ID**: `X-Correlation-Id` header (auto-generated UUID if omitted, propagated in logs and responses).
- **Idempotency Key**: `Idempotency-Key: <unique-string>` header mandatory on all mutation endpoints (`/payments`, `/refunds`, `/sandbox/funding`).

---

## 2. Standard Error Envelope (RFC 7807 Problem Details)

All 4xx and 5xx responses conform to RFC 7807:

```json
{
  "type": "https://api.ledgerflow.dev/errors/insufficient-funds",
  "title": "Insufficient Funds",
  "status": 422,
  "detail": "Customer wallet [w_8491] has available balance 2000 INR, but required 5000 INR.",
  "instance": "/api/v1/payments",
  "timestamp": "2026-10-17T01:45:00Z",
  "errorCode": "INSUFFICIENT_FUNDS",
  "invalidParams": []
}
```

Standard Error Codes:
- `IDEMPOTENCY_KEY_IN_USE`: Another request is currently processing this key (`409 Conflict`).
- `IDEMPOTENCY_PAYLOAD_MISMATCH`: The key was already processed with different parameters (`409 Conflict`).
- `INSUFFICIENT_FUNDS`: Wallet lacks balance (`422 Unprocessable Entity`).
- `REFUND_EXCEEDS_CAPACITY`: Refund amount exceeds remaining unrefunded capture (`422 Unprocessable Entity`).
- `INVALID_CURRENCY`: Currency mismatch between wallet and transaction (`400 Bad Request`).
- `RESOURCE_NOT_FOUND`: Referenced entity does not exist (`404 Not Found`).

---

## 3. Core Endpoint Specifications

### 3.1 Merchant Management
- `POST /api/v1/merchants`
  - Request: `{"name": "Acme Corp", "email": "billing@acme.com"}`
  - Response: `201 Created` with `id`, `name`, `email`, `status`.
- `POST /api/v1/merchants/{id}/keys`
  - Response: `201 Created` with `apiKey` (only returned once).

### 3.2 Wallets & Sandbox Funding
- `POST /api/v1/wallets`
  - Request: `{"customerId": "cust_123", "currency": "INR"}`
  - Response: `201 Created` with wallet ID, currency, and initial balance `0`.
- `GET /api/v1/wallets/{id}` & `GET /api/v1/wallets/{id}/balance`
  - Response: `200 OK` with `walletId`, `currency`, `balanceMinorUnits`, `balanceFormatted`.
- `POST /api/v1/sandbox/funding`
  - Headers: `Idempotency-Key: fund_001`
  - Request: `{"walletId": "uuid", "amount": 10000, "currency": "INR", "description": "Test initial deposit"}`
  - Response: `200 OK` with updated balance and linked `journalEntryId`.

### 3.3 Payment Processing
- `POST /api/v1/payments`
  - Headers: `Idempotency-Key: pay_req_9981`
  - Request:
    ```json
    {
      "customerWalletId": "w_849102",
      "amount": 5000,
      "currency": "INR",
      "description": "Subscription charge"
    }
    ```
  - Response:
    ```json
    {
      "id": "pay_554101",
      "status": "CAPTURED",
      "amount": 5000,
      "currency": "INR",
      "customerWalletId": "w_849102",
      "journalEntryId": "jrn_9921",
      "createdAt": "2026-10-17T01:45:00Z"
    }
    ```
- `GET /api/v1/payments/{id}`
  - Returns complete payment details, status history, and linked journal postings.

### 3.4 Refunds
- `POST /api/v1/payments/{id}/refunds`
  - Headers: `Idempotency-Key: ref_req_101`
  - Request: `{"amount": 2500, "reason": "Customer requested partial return"}`
  - Response: `201 Created` with `refundId`, `status: "COMPLETED"`, `remainingRefundableAmount: 2500`.

### 3.5 Ledger Explorer
- `GET /api/v1/ledger/journals/{id}`
  - Returns balanced journal entry:
    ```json
    {
      "id": "jrn_9921",
      "referenceType": "PAYMENT",
      "referenceId": "pay_554101",
      "isBalanced": true,
      "lines": [
        {"account": "LIABILITY:MERCHANT_PAYABLE", "direction": "CREDIT", "amount": 5000, "currency": "INR"},
        {"account": "ASSET:CUSTOMER_WALLET", "direction": "DEBIT", "amount": 5000, "currency": "INR"}
      ]
    }
    ```

### 3.6 Webhook Management & Mock Receiver
- `POST /api/v1/webhooks/subscriptions`
  - Request: `{"targetUrl": "http://localhost:8080/api/v1/mock/webhook-receiver", "events": ["payment.captured", "payment.refunded"]}`
  - Response: `201 Created` with webhook subscription ID and secret key.
- `POST /api/v1/mock/webhook-receiver`
  - Internal testing endpoint that receives webhook events, validates the HMAC-SHA256 signature, and records delivery metrics.

### 3.7 Reconciliation
- `POST /api/v1/reconciliation/run`
  - Triggers on-demand reconciliation run.
  - Response: `200 OK` with summary: total journals checked, balanced status, wallet reconciliation status, discrepancies.
