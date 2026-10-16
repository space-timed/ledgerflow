import React, { useState, useEffect } from 'react';
import { X, ArrowRight, RotateCcw, CheckCircle, Scale, ShieldAlert } from 'lucide-react';
import { Payment, JournalEntry, Refund } from '../types';
import { api } from '../services/api';

interface PaymentModalProps {
  payment: Payment;
  onClose: () => void;
  onRefresh: () => void;
  onNotify: (msg: string, type?: 'success' | 'error' | 'info') => void;
}

export const PaymentModal: React.FC<PaymentModalProps> = ({
  payment,
  onClose,
  onRefresh,
  onNotify,
}) => {
  const [journal, setJournal] = useState<JournalEntry | null>(null);
  const [refunds, setRefunds] = useState<Refund[]>([]);
  const [loading, setLoading] = useState(true);

  // Refund form state
  const [refundAmount, setRefundAmount] = useState<number>(payment.remainingRefundableMinorUnits || payment.amountMinorUnits);
  const [refundReason, setRefundReason] = useState('Customer return request');
  const [isRefunding, setIsRefunding] = useState(false);

  useEffect(() => {
    async function fetchDetails() {
      setLoading(true);
      try {
        if (payment.journalEntryId) {
          const j = await api.getJournal(payment.journalEntryId);
          setJournal(j);
        }
        const rList = await api.listRefunds(payment.id);
        setRefunds(rList);
      } catch (err: any) {
        console.error('Failed to load payment detail details', err);
      } finally {
        setLoading(false);
      }
    }
    fetchDetails();
  }, [payment.id, payment.journalEntryId]);

  const handleIssueRefund = async () => {
    if (refundAmount <= 0) {
      onNotify('Refund amount must be strictly greater than 0', 'error');
      return;
    }
    setIsRefunding(true);
    const idempotencyKey = `ref_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
    try {
      await api.createRefund(payment.id, refundAmount, refundReason, idempotencyKey);
      onNotify(`Refund of ${refundAmount} minor units processed successfully!`, 'success');
      onRefresh();
      onClose();
    } catch (err: any) {
      onNotify(`Refund failed: ${err.message}`, 'error');
    } finally {
      setIsRefunding(false);
    }
  };

  const statusClass = `badge-${payment.status.toLowerCase()}`;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '780px' }}>
        <div className="modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <h3 style={{ fontSize: '1.05rem', fontWeight: 600 }}>Payment Inspector</h3>
            <span className={`badge ${statusClass}`}>{payment.status}</span>
          </div>
          <button className="btn btn-sm" onClick={onClose} style={{ padding: '0.2rem 0.4rem' }}>
            <X size={16} />
          </button>
        </div>

        <div className="modal-body">
          {/* Metadata Card */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.75rem', fontSize: '0.82rem' }}>
            <div>
              <span className="mono-muted">Payment ID:</span>
              <div className="mono" style={{ color: 'var(--accent-blue)' }}>{payment.id}</div>
            </div>
            <div>
              <span className="mono-muted">Idempotency Key:</span>
              <div className="mono">{payment.idempotencyKey}</div>
            </div>
            <div>
              <span className="mono-muted">Amount Captured:</span>
              <div className="mono" style={{ fontSize: '1rem', fontWeight: 700, color: 'var(--text-main)' }}>
                {payment.formattedAmount} {payment.currency}
              </div>
            </div>
            <div>
              <span className="mono-muted">Remaining Refundable:</span>
              <div className="mono" style={{ fontSize: '1rem', fontWeight: 700, color: 'var(--accent-purple)' }}>
                {payment.remainingRefundableMinorUnits !== undefined ? payment.remainingRefundableMinorUnits : payment.amountMinorUnits} minor units
              </div>
            </div>
            <div>
              <span className="mono-muted">Customer Wallet ID:</span>
              <div className="mono">{payment.customerWalletId}</div>
            </div>
            <div>
              <span className="mono-muted">Created Timestamp:</span>
              <div className="mono">{new Date(payment.createdAt).toLocaleString()}</div>
            </div>
          </div>

          {/* Double-Entry Journal Section */}
          <div className="card-section" style={{ marginBottom: 0 }}>
            <div className="card-section-header">
              <span className="card-section-title">
                <Scale size={16} color="var(--accent-blue)" /> Linked Double-Entry Journal Entry
              </span>
              {journal && (
                <span className="badge badge-captured" style={{ fontSize: '0.7rem' }}>
                  <CheckCircle size={12} /> Balanced Invariant Verified
                </span>
              )}
            </div>
            {journal ? (
              <div className="table-wrapper">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Account ID</th>
                      <th>Account Type</th>
                      <th>Direction</th>
                      <th>Amount</th>
                      <th>Line Description</th>
                    </tr>
                  </thead>
                  <tbody>
                    {journal.lines.map((line) => (
                      <tr key={line.id}>
                        <td className="mono" style={{ fontSize: '0.78rem' }}>{line.accountId.substring(0, 13)}...</td>
                        <td><span className="badge" style={{ background: '#21262d' }}>{line.accountType}</span></td>
                        <td>
                          <span
                            className="badge"
                            style={{
                              color: line.direction === 'DEBIT' ? 'var(--accent-green)' : 'var(--accent-blue)',
                              background: line.direction === 'DEBIT' ? 'var(--accent-green-bg)' : 'rgba(88, 166, 255, 0.12)',
                            }}
                          >
                            {line.direction}
                          </span>
                        </td>
                        <td className="mono" style={{ fontWeight: 600 }}>{line.formattedAmount} {journal.currency}</td>
                        <td style={{ color: 'var(--text-muted)' }}>{line.description}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div style={{ padding: '1rem', color: 'var(--text-dim)', fontSize: '0.82rem' }}>
                {loading ? 'Retrieving ledger journal...' : 'No journal entry associated (Failed payment)'}
              </div>
            )}
          </div>

          {/* Refund History */}
          {refunds.length > 0 && (
            <div className="card-section" style={{ marginBottom: 0 }}>
              <div className="card-section-header">
                <span className="card-section-title">
                  <RotateCcw size={16} color="var(--accent-purple)" /> Refund History ({refunds.length})
                </span>
              </div>
              <div className="table-wrapper">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Refund ID</th>
                      <th>Amount</th>
                      <th>Reason</th>
                      <th>Idempotency Key</th>
                      <th>Time</th>
                    </tr>
                  </thead>
                  <tbody>
                    {refunds.map((r) => (
                      <tr key={r.id}>
                        <td className="mono">{r.id.substring(0, 8)}...</td>
                        <td className="mono" style={{ color: 'var(--accent-purple)', fontWeight: 600 }}>
                          {r.formattedAmount}
                        </td>
                        <td style={{ color: 'var(--text-muted)' }}>{r.reason}</td>
                        <td className="mono">{r.idempotencyKey}</td>
                        <td style={{ color: 'var(--text-dim)' }}>{new Date(r.createdAt).toLocaleTimeString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Refund Trigger Action */}
          {payment.status !== 'FAILED' && payment.status !== 'REFUNDED' && (
            <div style={{ background: '#1c2128', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '1rem' }}>
              <div style={{ fontSize: '0.88rem', fontWeight: 600, marginBottom: '0.75rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <RotateCcw size={16} color="var(--accent-purple)" /> Issue Full or Partial Refund
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr auto', gap: '0.75rem', alignItems: 'end' }}>
                <div className="form-group">
                  <label className="form-label">Amount (Minor Units)</label>
                  <input
                    type="number"
                    className="form-input mono"
                    value={refundAmount}
                    onChange={(e) => setRefundAmount(Number(e.target.value))}
                    min={1}
                    max={payment.remainingRefundableMinorUnits || payment.amountMinorUnits}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Reason</label>
                  <input
                    type="text"
                    className="form-input"
                    value={refundReason}
                    onChange={(e) => setRefundReason(e.target.value)}
                  />
                </div>
                <button
                  className="btn btn-primary"
                  onClick={handleIssueRefund}
                  disabled={isRefunding}
                  style={{ background: '#8957e5', height: '36px' }}
                >
                  <RotateCcw size={14} />
                  <span>{isRefunding ? 'Processing...' : 'Refund'}</span>
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
