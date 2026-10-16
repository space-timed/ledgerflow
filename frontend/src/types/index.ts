export interface Merchant {
  id: string;
  name: string;
  status: string;
  webhookUrl?: string;
  createdAt: string;
}

export interface ApiKey {
  id: string;
  keyPrefix: string;
  name: string;
  createdAt: string;
}

export interface Wallet {
  id: string;
  customerId: string;
  accountId: string;
  currency: string;
  balanceMinorUnits: number;
  formattedBalance: string;
  createdAt: string;
}

export interface WalletBalanceVerification {
  walletId: string;
  customerId: string;
  currency: string;
  cachedBalanceMinorUnits: number;
  formattedCachedBalance: string;
  ledgerVerified: boolean;
}

export interface JournalLine {
  id: string;
  accountId: string;
  accountType: 'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE';
  direction: 'DEBIT' | 'CREDIT';
  amountMinorUnits: number;
  formattedAmount: string;
  description: string;
}

export interface JournalEntry {
  id: string;
  referenceId: string;
  currency: string;
  totalDebitsMinorUnits: number;
  totalCreditsMinorUnits: number;
  balanced: boolean;
  createdAt: string;
  lines: JournalLine[];
}

export interface Payment {
  id: string;
  merchantId: string;
  customerWalletId: string;
  amountMinorUnits: number;
  formattedAmount: string;
  currency: string;
  status: 'CREATED' | 'CAPTURED' | 'FAILED' | 'PARTIALLY_REFUNDED' | 'REFUNDED';
  description?: string;
  idempotencyKey: string;
  journalEntryId?: string;
  createdAt: string;
  refundedAmountMinorUnits?: number;
  remainingRefundableMinorUnits?: number;
}

export interface Refund {
  id: string;
  paymentId: string;
  amountMinorUnits: number;
  formattedAmount: string;
  reason?: string;
  idempotencyKey: string;
  journalEntryId: string;
  status: string;
  createdAt: string;
}

export interface OutboxEvent {
  id: string;
  aggregateType: string;
  aggregateId: string;
  eventType: string;
  topic: string;
  partitionKey: string;
  payload: string;
  status: 'PENDING' | 'PUBLISHED' | 'FAILED';
  retryCount: number;
  lastError?: string;
  createdAt: string;
  publishedAt?: string;
}

export interface OutboxStats {
  pendingCount: number;
  publishedCount: number;
  failedCount: number;
  totalCount: number;
}

export interface WebhookSubscription {
  id: string;
  merchantId: string;
  targetUrl: string;
  events: string[];
  status: string;
  createdAt: string;
}

export interface WebhookDelivery {
  id: string;
  subscriptionId: string;
  eventId: string;
  eventType: string;
  status: 'PENDING' | 'DELIVERED' | 'FAILED';
  attemptCount: number;
  responseStatus?: number;
  errorMessage?: string;
  nextRetryAt?: string;
  deliveredAt?: string;
  createdAt: string;
}

export interface ReceivedWebhookEvent {
  receivedAt: string;
  signature: string;
  payload: string;
  verified: boolean;
}

export interface ReconciliationDiscrepancy {
  id: string;
  discrepancyType: string;
  entityType: string;
  entityId: string;
  expectedValue: string;
  actualValue: string;
  description: string;
  createdAt: string;
}

export interface ReconciliationRun {
  id: string;
  status: 'PASSED' | 'DISCREPANCIES_DETECTED' | 'FAILED';
  journalsChecked: number;
  walletsChecked: number;
  discrepanciesCount: number;
  createdAt: string;
  completedAt: string;
  discrepancies: ReconciliationDiscrepancy[];
}
