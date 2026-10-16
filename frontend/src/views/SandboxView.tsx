import React, { useState } from 'react';
import { Terminal, Plus, DollarSign, CreditCard, Webhook, CheckCircle2, RotateCcw, Copy } from 'lucide-react';
import { Wallet, Merchant } from '../types';
import { api } from '../services/api';

interface SandboxViewProps {
  merchants: Merchant[];
  wallets: Wallet[];
  onRefresh: () => void;
  onNotify: (msg: string, type?: 'success' | 'error' | 'info') => void;
  defaultFundWalletId?: string;
}

export const SandboxView: React.FC<SandboxViewProps> = ({
  merchants,
  wallets,
  onRefresh,
  onNotify,
  defaultFundWalletId,
}) => {
  const [activeTab, setActiveTab] = useState<'payment' | 'wallet' | 'merchant' | 'webhook'>('payment');

  // Merchant creation state
  const [merchantName, setMerchantName] = useState('Sandbox Store');
  const [createdKey, setCreatedKey] = useState<string | null>(null);

  // Wallet creation & funding state
  const [customerId, setCustomerId] = useState('cust_' + Math.random().toString(36).substring(2, 7));
  const [walletCurrency, setWalletCurrency] = useState('INR');
  const [selectedWalletId, setSelectedWalletId] = useState(defaultFundWalletId || (wallets[0]?.id || ''));
  const [fundingAmount, setFundingAmount] = useState(10000); // 100.00 in minor units

  // Payment state
  const [paymentWalletId, setPaymentWalletId] = useState(wallets[0]?.id || '');
  const [paymentAmount, setPaymentAmount] = useState(2500); // 25.00 in minor units
  const [paymentCurrency, setPaymentCurrency] = useState('INR');
  const [paymentDesc, setPaymentDesc] = useState('Order #9021');
  const [idempotencyKey, setIdempotencyKey] = useState('idemp_' + Math.random().toString(36).substring(2, 9));
  const [paymentResponse, setPaymentResponse] = useState<any>(null);
  const [isProcessing, setIsProcessing] = useState(false);

  // Webhook subscription state
  const [webhookUrl, setWebhookUrl] = useState('http://localhost:8080/api/v1/mock/webhook-receiver');

  // Handle Merchant Onboarding
  const handleOnboardMerchant = async () => {
    try {
      const m = await api.createMerchant(merchantName, webhookUrl);
      const keyRes = await api.createApiKey(m.id, 'Default Sandbox Key');
      setCreatedKey(keyRes.rawKey);
      api.setApiKey(keyRes.rawKey);
      onNotify(`Merchant [${m.name}] created and API key activated!`, 'success');
      onRefresh();
    } catch (err: any) {
      onNotify(`Failed to onboard merchant: ${err.message}`, 'error');
    }
  };

  // Handle Wallet Creation
  const handleCreateWallet = async () => {
    try {
      const w = await api.createWallet(customerId, walletCurrency);
      onNotify(`Customer wallet created [${w.id}]!`, 'success');
      setSelectedWalletId(w.id);
      setPaymentWalletId(w.id);
      setPaymentCurrency(w.currency);
      onRefresh();
    } catch (err: any) {
      onNotify(`Failed to create wallet: ${err.message}`, 'error');
    }
  };

  // Handle Wallet Funding
  const handleFundWallet = async () => {
    if (!selectedWalletId) {
      onNotify('Please select a wallet to fund', 'error');
      return;
    }
    const fundingKey = 'fnd_' + Math.random().toString(36).substring(2, 9);
    try {
      await api.fundWallet(selectedWalletId, fundingAmount, walletCurrency, 'Sandbox simulated funding', fundingKey);
      onNotify(`Wallet funded with ${(fundingAmount / 100).toFixed(2)} ${walletCurrency}! Balanced double-entry journal posted.`, 'success');
      onRefresh();
    } catch (err: any) {
      onNotify(`Funding failed: ${err.message}`, 'error');
    }
  };

  // Handle Payment Capture
  const handleProcessPayment = async () => {
    if (!paymentWalletId) {
      onNotify('Please select a customer wallet', 'error');
      return;
    }
    setIsProcessing(true);
    setPaymentResponse(null);
    try {
      const res = await api.createPayment(
        paymentWalletId,
        paymentAmount,
        paymentCurrency,
        paymentDesc,
        idempotencyKey
      );
      setPaymentResponse({ type: 'success', data: res });
      onNotify(`Payment captured successfully! Journal: ${res.journalEntryId?.substring(0, 8)}...`, 'success');
      onRefresh();
    } catch (err: any) {
      setPaymentResponse({ type: 'error', message: err.message });
      onNotify(`Payment error: ${err.message}`, 'error');
    } finally {
      setIsProcessing(false);
    }
  };

  // Handle Webhook Subscription
  const handleSubscribeWebhook = async () => {
    try {
      await api.createWebhookSubscription(webhookUrl, [
        'payment.created',
        'payment.captured',
        'payment.failed',
        'payment.refunded',
        'wallet.funded',
      ]);
      onNotify('Webhook subscription created targeting ' + webhookUrl, 'success');
      onRefresh();
    } catch (err: any) {
      onNotify(`Failed to subscribe webhook: ${err.message}`, 'error');
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">
            <Terminal size={22} color="var(--accent-blue)" /> Interactive Sandbox Workbench
          </h1>
          <p className="page-subtitle">
            Simulate end-to-end payment flows, test idempotency replays, and verify outbox events in real-time
          </p>
        </div>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.5rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '0.5rem' }}>
        <button
          className={`btn ${activeTab === 'payment' ? 'btn-primary' : ''}`}
          onClick={() => setActiveTab('payment')}
        >
          <CreditCard size={14} /> Process Payment & Idempotency
        </button>
        <button
          className={`btn ${activeTab === 'wallet' ? 'btn-primary' : ''}`}
          onClick={() => setActiveTab('wallet')}
        >
          <DollarSign size={14} /> Customer Wallets & Funding
        </button>
        <button
          className={`btn ${activeTab === 'merchant' ? 'btn-primary' : ''}`}
          onClick={() => setActiveTab('merchant')}
        >
          <Plus size={14} /> Merchant Onboarding
        </button>
        <button
          className={`btn ${activeTab === 'webhook' ? 'btn-primary' : ''}`}
          onClick={() => setActiveTab('webhook')}
        >
          <Webhook size={14} /> Webhook Subscriptions
        </button>
      </div>

      {/* Tab 1: Process Payment */}
      {activeTab === 'payment' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: '1.5rem' }}>
          <div className="card-section" style={{ padding: '1.25rem' }}>
            <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '1rem' }}>Capture Payment</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div className="form-group">
                <label className="form-label">Customer Wallet</label>
                <select
                  className="form-select"
                  value={paymentWalletId}
                  onChange={(e) => {
                    setPaymentWalletId(e.target.value);
                    const matched = wallets.find((w) => w.id === e.target.value);
                    if (matched) setPaymentCurrency(matched.currency);
                  }}
                >
                  <option value="">Select a customer wallet...</option>
                  {wallets.map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.customerId} — {w.formattedBalance} (ID: {w.id.substring(0, 8)}...)
                    </option>
                  ))}
                </select>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                <div className="form-group">
                  <label className="form-label">Amount (Minor Units)</label>
                  <input
                    type="number"
                    className="form-input mono"
                    value={paymentAmount}
                    onChange={(e) => setPaymentAmount(Number(e.target.value))}
                    min={1}
                  />
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>
                    = {(paymentAmount / 100).toFixed(2)} {paymentCurrency}
                  </span>
                </div>
                <div className="form-group">
                  <label className="form-label">Currency</label>
                  <input type="text" className="form-input mono" value={paymentCurrency} disabled />
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">Description</label>
                <input
                  type="text"
                  className="form-input"
                  value={paymentDesc}
                  onChange={(e) => setPaymentDesc(e.target.value)}
                />
              </div>

              <div className="form-group">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <label className="form-label">Idempotency-Key Header</label>
                  <button
                    className="btn btn-sm"
                    onClick={() => setIdempotencyKey('idemp_' + Math.random().toString(36).substring(2, 9))}
                    style={{ fontSize: '0.72rem', padding: '0.1rem 0.4rem' }}
                  >
                    Regenerate
                  </button>
                </div>
                <input
                  type="text"
                  className="form-input mono"
                  value={idempotencyKey}
                  onChange={(e) => setIdempotencyKey(e.target.value)}
                />
                <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>
                  Same key with same payload will replay cached response without double-charging.
                </span>
              </div>

              <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem' }}>
                <button
                  className="btn btn-primary"
                  onClick={handleProcessPayment}
                  disabled={isProcessing || !paymentWalletId}
                  style={{ flex: 1 }}
                >
                  <CreditCard size={14} />
                  <span>{isProcessing ? 'Processing...' : 'Process Payment'}</span>
                </button>
                <button
                  className="btn"
                  onClick={handleProcessPayment}
                  disabled={isProcessing || !paymentWalletId}
                  title="Resends identical request to demonstrate idempotency caching"
                >
                  <RotateCcw size={14} />
                  <span>Test Replay</span>
                </button>
              </div>
            </div>
          </div>

          {/* Response Inspector */}
          <div className="card-section" style={{ padding: '1.25rem' }}>
            <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' }}>Response Result</h3>
            {paymentResponse ? (
              <pre
                className="mono"
                style={{
                  background: '#090d13',
                  padding: '1rem',
                  borderRadius: '6px',
                  border: `1px solid ${paymentResponse.type === 'success' ? 'var(--accent-green)' : 'var(--accent-red)'}`,
                  fontSize: '0.8rem',
                  lineHeight: '1.4',
                  color: paymentResponse.type === 'success' ? '#7ee787' : 'var(--accent-red)',
                  maxHeight: '320px',
                  overflowY: 'auto',
                }}
              >
                {JSON.stringify(paymentResponse.data || paymentResponse.message, null, 2)}
              </pre>
            ) : (
              <div style={{ color: 'var(--text-dim)', fontSize: '0.82rem', padding: '2rem', textAlign: 'center' }}>
                Click "Process Payment" to trigger the engine and inspect the JSON response.
              </div>
            )}
          </div>
        </div>
      )}

      {/* Tab 2: Wallets & Funding */}
      {activeTab === 'wallet' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
          {/* Create Wallet */}
          <div className="card-section" style={{ padding: '1.25rem' }}>
            <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '1rem' }}>Create Customer Wallet</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div className="form-group">
                <label className="form-label">Customer Identifier</label>
                <input
                  type="text"
                  className="form-input mono"
                  value={customerId}
                  onChange={(e) => setCustomerId(e.target.value)}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Currency</label>
                <select
                  className="form-select"
                  value={walletCurrency}
                  onChange={(e) => setWalletCurrency(e.target.value)}
                >
                  <option value="INR">INR — Indian Rupee</option>
                  <option value="USD">USD — US Dollar</option>
                  <option value="EUR">EUR — Euro</option>
                  <option value="GBP">GBP — British Pound</option>
                </select>
              </div>
              <button className="btn btn-primary" onClick={handleCreateWallet}>
                <Plus size={14} /> Create Wallet
              </button>
            </div>
          </div>

          {/* Fund Wallet */}
          <div className="card-section" style={{ padding: '1.25rem' }}>
            <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '1rem' }}>Fund Wallet (Sandbox)</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div className="form-group">
                <label className="form-label">Target Wallet</label>
                <select
                  className="form-select"
                  value={selectedWalletId}
                  onChange={(e) => setSelectedWalletId(e.target.value)}
                >
                  <option value="">Select wallet to fund...</option>
                  {wallets.map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.customerId} — {w.formattedBalance}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label className="form-label">Funding Amount (Minor Units)</label>
                <input
                  type="number"
                  className="form-input mono"
                  value={fundingAmount}
                  onChange={(e) => setFundingAmount(Number(e.target.value))}
                  min={1}
                />
                <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>
                  = {(fundingAmount / 100).toFixed(2)} {walletCurrency}
                </span>
              </div>
              <button className="btn btn-primary" onClick={handleFundWallet}>
                <DollarSign size={14} /> Post Balanced Funding Journal
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Tab 3: Merchant Onboarding */}
      {activeTab === 'merchant' && (
        <div className="card-section" style={{ padding: '1.25rem', maxWidth: '600px' }}>
          <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '1rem' }}>Onboard New Merchant</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label">Merchant Name</label>
              <input
                type="text"
                className="form-input"
                value={merchantName}
                onChange={(e) => setMerchantName(e.target.value)}
              />
            </div>
            <div className="form-group">
              <label className="form-label">Webhook Endpoint (Optional)</label>
              <input
                type="text"
                className="form-input mono"
                value={webhookUrl}
                onChange={(e) => setWebhookUrl(e.target.value)}
              />
            </div>
            <button className="btn btn-primary" onClick={handleOnboardMerchant}>
              <Plus size={14} /> Register Merchant & Generate Key
            </button>

            {createdKey && (
              <div
                style={{
                  marginTop: '1rem',
                  padding: '1rem',
                  background: 'rgba(63, 185, 80, 0.1)',
                  border: '1px solid rgba(63, 185, 80, 0.4)',
                  borderRadius: '6px',
                }}
              >
                <div style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--accent-green)', marginBottom: '0.35rem' }}>
                  <CheckCircle2 size={16} /> API Key Generated & Activated:
                </div>
                <div className="mono" style={{ fontSize: '0.82rem', wordBreak: 'break-all', color: '#fff' }}>
                  {createdKey}
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Tab 4: Webhook Subscriptions */}
      {activeTab === 'webhook' && (
        <div className="card-section" style={{ padding: '1.25rem', maxWidth: '600px' }}>
          <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '1rem' }}>Create Webhook Subscription</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label">Target URL</label>
              <input
                type="text"
                className="form-input mono"
                value={webhookUrl}
                onChange={(e) => setWebhookUrl(e.target.value)}
              />
            </div>
            <div style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>
              Subscribes to: <code>payment.created</code>, <code>payment.captured</code>, <code>payment.failed</code>, <code>payment.refunded</code>, <code>wallet.funded</code>.
            </div>
            <button className="btn btn-primary" onClick={handleSubscribeWebhook}>
              <Webhook size={14} /> Subscribe Endpoint
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
