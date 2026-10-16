import React, { useState } from 'react';
import { CreditCard, Search, Filter, RotateCcw, ArrowUpRight } from 'lucide-react';
import { Payment } from '../types';

interface PaymentsViewProps {
  payments: Payment[];
  onSelectPayment: (p: Payment) => void;
  onRefresh: () => void;
}

export const PaymentsView: React.FC<PaymentsViewProps> = ({
  payments,
  onSelectPayment,
  onRefresh,
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  const filteredPayments = payments.filter((p) => {
    const matchesSearch =
      p.id.toLowerCase().includes(searchTerm.toLowerCase()) ||
      p.customerWalletId.toLowerCase().includes(searchTerm.toLowerCase()) ||
      p.idempotencyKey.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (p.description && p.description.toLowerCase().includes(searchTerm.toLowerCase()));

    const matchesStatus = statusFilter === 'ALL' || p.status === statusFilter;
    return matchesSearch && matchesStatus;
  });

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">
            <CreditCard size={22} color="var(--accent-blue)" /> Payments & Refunds
          </h1>
          <p className="page-subtitle">Idempotent transactions, lifecycle audits, and linked double-entry journals</p>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div style={{ display: 'flex', gap: '1rem', marginBottom: '1.25rem', alignItems: 'center' }}>
        <div style={{ position: 'relative', flex: 1 }}>
          <Search size={16} color="var(--text-dim)" style={{ position: 'absolute', left: '12px', top: '10px' }} />
          <input
            type="text"
            className="form-input"
            style={{ width: '100%', paddingLeft: '2.3rem' }}
            placeholder="Search by Payment ID, Wallet ID, or Idempotency Key..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <Filter size={16} color="var(--text-dim)" />
          <select
            className="form-select"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="ALL">All Statuses</option>
            <option value="CAPTURED">CAPTURED</option>
            <option value="PARTIALLY_REFUNDED">PARTIALLY_REFUNDED</option>
            <option value="REFUNDED">REFUNDED</option>
            <option value="FAILED">FAILED</option>
            <option value="CREATED">CREATED</option>
          </select>
        </div>
      </div>

      {/* Table */}
      <div className="card-section">
        <div className="table-wrapper">
          <table className="data-table">
            <thead>
              <tr>
                <th>Payment ID</th>
                <th>Status</th>
                <th>Captured Amount</th>
                <th>Currency</th>
                <th>Remaining Refundable</th>
                <th>Idempotency Key</th>
                <th>Timestamp</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredPayments.length === 0 ? (
                <tr>
                  <td colSpan={8} style={{ textAlign: 'center', padding: '2.5rem', color: 'var(--text-dim)' }}>
                    No payments match your filter criteria.
                  </td>
                </tr>
              ) : (
                filteredPayments.map((p) => {
                  const statusClass = `badge-${p.status.toLowerCase()}`;
                  return (
                    <tr key={p.id}>
                      <td className="mono" style={{ color: 'var(--accent-blue)', fontWeight: 600 }}>
                        {p.id.substring(0, 8)}...{p.id.substring(p.id.length - 4)}
                      </td>
                      <td>
                        <span className={`badge ${statusClass}`}>{p.status}</span>
                      </td>
                      <td className="mono" style={{ fontWeight: 600, color: 'var(--text-main)' }}>
                        {p.formattedAmount}
                      </td>
                      <td className="mono">{p.currency}</td>
                      <td className="mono" style={{ color: 'var(--accent-purple)' }}>
                        {p.remainingRefundableMinorUnits !== undefined
                          ? p.remainingRefundableMinorUnits
                          : p.amountMinorUnits}{' '}
                        units
                      </td>
                      <td className="mono" style={{ fontSize: '0.78rem', color: 'var(--text-dim)' }}>
                        {p.idempotencyKey.length > 20
                          ? `${p.idempotencyKey.substring(0, 18)}...`
                          : p.idempotencyKey}
                      </td>
                      <td style={{ color: 'var(--text-dim)', fontSize: '0.8rem' }}>
                        {new Date(p.createdAt).toLocaleTimeString()}
                      </td>
                      <td>
                        <button className="btn btn-sm" onClick={() => onSelectPayment(p)}>
                          <span>Inspect</span>
                          <ArrowUpRight size={12} />
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
