import {
  DollarSign,
  Users,
  BookOpen,
  Send,
  ShieldCheck,
  CheckCircle2,
  AlertTriangle,
  ArrowUpRight,
  Sparkles,
  Terminal,
} from 'lucide-react';
import { Payment, Wallet, JournalEntry, OutboxStats, ReconciliationRun } from '../types';
import { api } from '../services/api';

interface OverviewViewProps {
  payments: Payment[];
  wallets: Wallet[];
  journals: JournalEntry[];
  outboxStats: OutboxStats | null;
  latestReconciliation: ReconciliationRun | null;
  onSelectPayment: (p: Payment) => void;
}

export const OverviewView: React.FC<OverviewViewProps> = ({
  payments,
  wallets,
  journals,
  outboxStats,
  latestReconciliation,
  onSelectPayment,
}) => {
  // Aggregate stats
  const totalVolumeMinor = payments
    .filter((p) => p.status === 'CAPTURED' || p.status === 'PARTIALLY_REFUNDED' || p.status === 'REFUNDED')
    .reduce((acc, p) => acc + p.amountMinorUnits, 0);

  const capturedCount = payments.filter((p) => p.status === 'CAPTURED').length;
  const failedCount = payments.filter((p) => p.status === 'FAILED').length;
  const refundedCount = payments.filter((p) => p.status === 'REFUNDED' || p.status === 'PARTIALLY_REFUNDED').length;

  const reconPassed = latestReconciliation?.status === 'PASSED';

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Operational Overview</h1>
          <p className="page-subtitle">Real-time health, transaction throughput, and accounting invariant monitoring</p>
        </div>
      </div>

      {!api.getApiKey() && (
        <div style={{
          backgroundColor: '#0d1d31',
          border: '1px solid #1d4ed8',
          borderRadius: '8px',
          padding: '1.25rem 1.5rem',
          marginBottom: '1.75rem',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '1.5rem'
        }}>
          <div>
            <div style={{ fontWeight: 600, color: '#60a5fa', marginBottom: '0.35rem', display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '1rem' }}>
              <Sparkles size={18} color="#60a5fa" />
              <span>Multi-Tenant Sandbox Session Not Yet Selected</span>
            </div>
            <p style={{ margin: 0, fontSize: '0.875rem', color: '#94a3b8', lineHeight: 1.5 }}>
              LedgerFlow enforces multi-tenant merchant isolation. To view transactions, double-entry journals, and customer wallets, load the pre-seeded demo merchant or use <strong>"Sandbox Simulator"</strong> in the sidebar.
            </p>
          </div>
          <div style={{ display: 'flex', gap: '0.75rem', flexShrink: 0 }}>
            <button
              className="btn btn-primary"
              style={{ padding: '0.55rem 1.1rem', fontSize: '0.85rem' }}
              onClick={() => {
                api.setApiKey('lf_live_fdjxCAdQBtTpu5GytPKYkRqjkk-KhUKE');
                window.location.reload();
              }}
            >
              <Sparkles size={14} /> Load Demo Merchant (1-Click)
            </button>
          </div>
        </div>
      )}

      {/* KPI Grid */}
      <div className="kpi-grid">
        <div className="kpi-card">
          <div className="kpi-header">
            <span>TOTAL CAPTURED VOLUME</span>
            <DollarSign size={16} color="var(--accent-green)" />
          </div>
          <div className="kpi-value" style={{ color: 'var(--accent-green)' }}>
            {(totalVolumeMinor / 100).toFixed(2)}
          </div>
          <div className="kpi-subtext">Across {payments.length} total payment intents</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span>ACTIVE WALLETS</span>
            <Users size={16} color="var(--accent-blue)" />
          </div>
          <div className="kpi-value">{wallets.length}</div>
          <div className="kpi-subtext">Verified double-entry asset balances</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span>LEDGER JOURNALS</span>
            <BookOpen size={16} color="var(--accent-purple)" />
          </div>
          <div className="kpi-value">{journals.length}</div>
          <div className="kpi-subtext">100% Balanced debits & credits</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span>TRANSACTIONAL OUTBOX</span>
            <Send size={16} color="var(--accent-yellow)" />
          </div>
          <div className="kpi-value" style={{ color: outboxStats?.pendingCount ? 'var(--accent-yellow)' : 'var(--text-main)' }}>
            {outboxStats?.pendingCount ?? 0}
          </div>
          <div className="kpi-subtext">
            {outboxStats?.publishedCount ?? 0} published to Kafka
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span>RECONCILIATION AUDIT</span>
            <ShieldCheck size={16} color={reconPassed ? 'var(--accent-green)' : 'var(--accent-red)'} />
          </div>
          <div
            className="kpi-value"
            style={{
              fontSize: '1.25rem',
              color: reconPassed ? 'var(--accent-green)' : 'var(--accent-red)',
            }}
          >
            {latestReconciliation ? latestReconciliation.status : 'PENDING RUN'}
          </div>
          <div className="kpi-subtext">
            {latestReconciliation
              ? `${latestReconciliation.discrepanciesCount} discrepancies detected`
              : 'Audit not yet triggered'}
          </div>
        </div>
      </div>

      {/* Two Column Layout: Status Distribution & Engine Architecture */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', marginBottom: '1.5rem' }}>
        {/* Payment Breakdown */}
        <div className="card-section">
          <div className="card-section-header">
            <span className="card-section-title">Payment Lifecycle Distribution</span>
          </div>
          <div style={{ padding: '1.25rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.82rem', marginBottom: '0.35rem' }}>
                <span style={{ color: 'var(--accent-green)', fontWeight: 600 }}>CAPTURED</span>
                <span className="mono">{capturedCount} ({payments.length ? Math.round((capturedCount / payments.length) * 100) : 0}%)</span>
              </div>
              <div style={{ height: '8px', background: '#21262d', borderRadius: '4px', overflow: 'hidden' }}>
                <div
                  style={{
                    height: '100%',
                    background: 'var(--accent-green)',
                    width: `${payments.length ? (capturedCount / payments.length) * 100 : 0}%`,
                  }}
                />
              </div>
            </div>

            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.82rem', marginBottom: '0.35rem' }}>
                <span style={{ color: 'var(--accent-purple)', fontWeight: 600 }}>REFUNDED / PARTIAL</span>
                <span className="mono">{refundedCount} ({payments.length ? Math.round((refundedCount / payments.length) * 100) : 0}%)</span>
              </div>
              <div style={{ height: '8px', background: '#21262d', borderRadius: '4px', overflow: 'hidden' }}>
                <div
                  style={{
                    height: '100%',
                    background: 'var(--accent-purple)',
                    width: `${payments.length ? (refundedCount / payments.length) * 100 : 0}%`,
                  }}
                />
              </div>
            </div>

            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.82rem', marginBottom: '0.35rem' }}>
                <span style={{ color: 'var(--accent-red)', fontWeight: 600 }}>FAILED / DECLINED</span>
                <span className="mono">{failedCount} ({payments.length ? Math.round((failedCount / payments.length) * 100) : 0}%)</span>
              </div>
              <div style={{ height: '8px', background: '#21262d', borderRadius: '4px', overflow: 'hidden' }}>
                <div
                  style={{
                    height: '100%',
                    background: 'var(--accent-red)',
                    width: `${payments.length ? (failedCount / payments.length) * 100 : 0}%`,
                  }}
                />
              </div>
            </div>
          </div>
        </div>

        {/* Distributed Guarantees Card */}
        <div className="card-section">
          <div className="card-section-header">
            <span className="card-section-title">Distributed Architectural Guarantees</span>
          </div>
          <div style={{ padding: '1.25rem', display: 'flex', flexDirection: 'column', gap: '0.75rem', fontSize: '0.83rem' }}>
            <div style={{ display: 'flex', gap: '0.65rem' }}>
              <CheckCircle2 size={16} color="var(--accent-green)" style={{ flexShrink: 0, marginTop: '2px' }} />
              <div>
                <strong>Double-Entry Invariant:</strong> Every balance alteration executes as a balanced journal entry where sum(Debits) == sum(Credits).
              </div>
            </div>
            <div style={{ display: 'flex', gap: '0.65rem' }}>
              <CheckCircle2 size={16} color="var(--accent-green)" style={{ flexShrink: 0, marginTop: '2px' }} />
              <div>
                <strong>Strict Concurrency Safety:</strong> Pessimistic row locking on customer wallets guarantees zero negative balances or race condition double-spending under concurrent traffic.
              </div>
            </div>
            <div style={{ display: 'flex', gap: '0.65rem' }}>
              <CheckCircle2 size={16} color="var(--accent-green)" style={{ flexShrink: 0, marginTop: '2px' }} />
              <div>
                <strong>Transactional Outbox:</strong> Atomically stages domain events into PostgreSQL; background daemon drains to Kafka with SKIP LOCKED & exponential backoff.
              </div>
            </div>
            <div style={{ display: 'flex', gap: '0.65rem' }}>
              <CheckCircle2 size={16} color="var(--accent-green)" style={{ flexShrink: 0, marginTop: '2px' }} />
              <div>
                <strong>HMAC-SHA256 Webhooks:</strong> Webhook payloads are cryptographically signed with timestamped HMAC headers and delivered with exponential backoff.
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Recent Activity Table */}
      <div className="card-section">
        <div className="card-section-header">
          <span className="card-section-title">Recent Transactions</span>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-dim)' }}>Showing latest {Math.min(payments.length, 5)} payments</span>
        </div>
        <div className="table-wrapper">
          <table className="data-table">
            <thead>
              <tr>
                <th>Payment ID</th>
                <th>Status</th>
                <th>Amount</th>
                <th>Currency</th>
                <th>Description</th>
                <th>Timestamp</th>
                <th>Inspect</th>
              </tr>
            </thead>
            <tbody>
              {payments.length === 0 ? (
                <tr>
                  <td colSpan={7} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-dim)' }}>
                    No transactions found. Use the Interactive Sandbox to execute your first payment!
                  </td>
                </tr>
              ) : (
                payments.slice(0, 5).map((p) => (
                  <tr key={p.id}>
                    <td className="mono" style={{ color: 'var(--accent-blue)' }}>{p.id.substring(0, 8)}...</td>
                    <td>
                      <span className={`badge badge-${p.status.toLowerCase()}`}>{p.status}</span>
                    </td>
                    <td className="mono" style={{ fontWeight: 600 }}>{p.formattedAmount}</td>
                    <td className="mono">{p.currency}</td>
                    <td style={{ color: 'var(--text-muted)' }}>{p.description || '—'}</td>
                    <td style={{ color: 'var(--text-dim)' }}>{new Date(p.createdAt).toLocaleTimeString()}</td>
                    <td>
                      <button className="btn btn-sm" onClick={() => onSelectPayment(p)}>
                        <span>Inspect</span>
                        <ArrowUpRight size={12} />
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
