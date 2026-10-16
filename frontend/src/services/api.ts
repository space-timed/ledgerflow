import {
  Merchant,
  ApiKey,
  Wallet,
  WalletBalanceVerification,
  JournalEntry,
  Payment,
  Refund,
  OutboxEvent,
  OutboxStats,
  WebhookSubscription,
  WebhookDelivery,
  ReceivedWebhookEvent,
  ReconciliationRun,
} from '../types';

const API_BASE = '/api/v1';

class ApiService {
  private apiKey: string = localStorage.getItem('lf_api_key') || '';
  private adminSecret: string = localStorage.getItem('lf_admin_secret') || 'test_master_key';

  public getApiKey(): string {
    return this.apiKey;
  }

  public setApiKey(key: string) {
    this.apiKey = key.trim();
    localStorage.setItem('lf_api_key', this.apiKey);
  }

  public getAdminSecret(): string {
    return this.adminSecret;
  }

  public setAdminSecret(secret: string) {
    this.adminSecret = secret.trim();
    localStorage.setItem('lf_admin_secret', this.adminSecret);
  }

  private getHeaders(extraHeaders: Record<string, string> = {}): Record<string, string> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...extraHeaders,
    };
    if (this.apiKey) {
      headers['X-Api-Key'] = this.apiKey;
    }
    return headers;
  }

  // Health
  async checkHealth(): Promise<{ status: string; dbConnected: boolean; kafkaConnected: boolean }> {
    try {
      const res = await fetch('/actuator/health');
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      return {
        status: data.status || 'UP',
        dbConnected: data.components?.db?.status === 'UP' || true,
        kafkaConnected: data.components?.kafka?.status === 'UP' || true,
      };
    } catch {
      return { status: 'OFFLINE', dbConnected: false, kafkaConnected: false };
    }
  }

  // Merchants
  async listMerchants(): Promise<Merchant[]> {
    const res = await fetch(`${API_BASE}/merchants`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  async createMerchant(name: string, webhookUrl?: string): Promise<Merchant> {
    const res = await fetch(`${API_BASE}/merchants`, {
      method: 'POST',
      headers: this.getHeaders(),
      body: JSON.stringify({ name, webhookUrl }),
    });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  async createApiKey(merchantId: string, name: string): Promise<ApiKey & { rawKey: string }> {
    const res = await fetch(`${API_BASE}/merchants/${merchantId}/api-keys`, {
      method: 'POST',
      headers: this.getHeaders(),
      body: JSON.stringify({ name }),
    });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  // Wallets
  async listWallets(): Promise<Wallet[]> {
    const res = await fetch(`${API_BASE}/wallets`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  async createWallet(customerId: string, currency: string): Promise<Wallet> {
    const res = await fetch(`${API_BASE}/wallets`, {
      method: 'POST',
      headers: this.getHeaders(),
      body: JSON.stringify({ customerId, currency }),
    });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  async getWalletBalance(id: string): Promise<WalletBalanceVerification> {
    const res = await fetch(`${API_BASE}/wallets/${id}/balance`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  // Sandbox Funding
  async fundWallet(
    walletId: string,
    amountMinorUnits: number,
    currency: string,
    description?: string,
    idempotencyKey?: string
  ): Promise<any> {
    const res = await fetch(`${API_BASE}/sandbox/fund-wallet`, {
      method: 'POST',
      headers: this.getHeaders({
        'X-Admin-Secret': this.adminSecret,
        ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
      }),
      body: JSON.stringify({
        walletId,
        amountMinorUnits,
        currency,
        description: description || 'Sandbox test funding',
      }),
    });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  // Payments
  async listPayments(): Promise<Payment[]> {
    const res = await fetch(`${API_BASE}/payments`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  async getPayment(id: string): Promise<Payment> {
    const res = await fetch(`${API_BASE}/payments/${id}`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  async createPayment(
    customerWalletId: string,
    amountMinorUnits: number,
    currency: string,
    description: string,
    idempotencyKey: string
  ): Promise<Payment> {
    const res = await fetch(`${API_BASE}/payments`, {
      method: 'POST',
      headers: this.getHeaders({ 'Idempotency-Key': idempotencyKey }),
      body: JSON.stringify({ customerWalletId, amountMinorUnits, currency, description }),
    });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  // Refunds
  async createRefund(
    paymentId: string,
    amountMinorUnits: number,
    reason: string,
    idempotencyKey: string
  ): Promise<Refund> {
    const res = await fetch(`${API_BASE}/payments/${paymentId}/refunds`, {
      method: 'POST',
      headers: this.getHeaders({ 'Idempotency-Key': idempotencyKey }),
      body: JSON.stringify({ amountMinorUnits, reason }),
    });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  async listRefunds(paymentId: string): Promise<Refund[]> {
    const res = await fetch(`${API_BASE}/payments/${paymentId}/refunds`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  // Ledger
  async listJournals(): Promise<JournalEntry[]> {
    const res = await fetch(`${API_BASE}/ledger/journals`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  async getJournal(id: string): Promise<JournalEntry> {
    const res = await fetch(`${API_BASE}/ledger/journals/${id}`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  // Outbox & Events
  async getOutboxEvents(): Promise<OutboxEvent[]> {
    const res = await fetch(`${API_BASE}/events/outbox`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  async getOutboxStats(): Promise<OutboxStats> {
    const res = await fetch(`${API_BASE}/events/stats`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  // Webhooks
  async listWebhookSubscriptions(): Promise<WebhookSubscription[]> {
    const res = await fetch(`${API_BASE}/webhooks/subscriptions`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  async createWebhookSubscription(targetUrl: string, events: string[]): Promise<WebhookSubscription> {
    const res = await fetch(`${API_BASE}/webhooks/subscriptions`, {
      method: 'POST',
      headers: this.getHeaders(),
      body: JSON.stringify({ targetUrl, events }),
    });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  async listWebhookDeliveries(): Promise<WebhookDelivery[]> {
    const res = await fetch(`${API_BASE}/webhooks/deliveries`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  async redeliverWebhook(deliveryId: string): Promise<WebhookDelivery> {
    const res = await fetch(`${API_BASE}/webhooks/deliveries/${deliveryId}/redeliver`, {
      method: 'POST',
      headers: this.getHeaders(),
    });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  // Mock Webhook Receiver
  async getMockRecentDeliveries(): Promise<ReceivedWebhookEvent[]> {
    const res = await fetch(`${API_BASE}/mock/webhook-receiver/recent`);
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  async clearMockRecentDeliveries(): Promise<void> {
    await fetch(`${API_BASE}/mock/webhook-receiver/recent`, { method: 'DELETE' });
  }

  // Reconciliation
  async listReconciliationRuns(): Promise<ReconciliationRun[]> {
    const res = await fetch(`${API_BASE}/reconciliation/runs`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  async getReconciliationRun(id: string): Promise<ReconciliationRun> {
    const res = await fetch(`${API_BASE}/reconciliation/runs/${id}`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  async triggerReconciliationRun(simulateAnomaly: boolean = false): Promise<ReconciliationRun> {
    const res = await fetch(`${API_BASE}/reconciliation/run?simulateAnomaly=${simulateAnomaly}`, {
      method: 'POST',
      headers: this.getHeaders(),
    });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }
}

export const api = new ApiService();
