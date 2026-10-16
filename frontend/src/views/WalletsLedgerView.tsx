import React, { useState } from 'react';
import { BookOpen, Users, CheckCircle, Plus, Scale, ChevronDown, ChevronRight, ShieldCheck } from 'lucide-react';
import { Wallet, JournalEntry } from '../types';
import { api } from '../services/api';

interface WalletsLedgerViewProps {
  wallets: Wallet[];
  journals: JournalEntry[];
  onRefresh: () => void;
  onNotify: (msg: string, type?: 'success' | 'error' | 'info') => void;
  onOpenSandboxFunding: (walletId: string) => void;
}

export const WalletsLedgerView: React.FC<WalletsLedgerViewProps> = ({
  wallets,
  journals,
  onRefresh,
  onNotify,
  onOpenSandboxFunding,
}) => {
  const [expandedJournalId, setExpandedJournalId] = useState<string | null>(null);
  const [verifyingWalletId, setVerifyingWalletId] = useState<string | null>(null);

  const handleVerifyWallet = async (walletId: string) => {
    setVerifyingWalletId(walletId);
    try {
      const res = await api.getWalletBalance(walletId);
      if (res.ledgerVerified) {
        onNotify(`Wallet verified! Cached balance matches ledger journal sum (${res.formattedCachedBalance})`, 'success');
      } else {
        onNotify(`CRITICAL MISMATCH: Wallet cached balance diverges from ledger!`, 'error');
      }
    } catch (err: any) {
      onNotify(`Verification failed: ${err.message}`, 'error');
    } finally {
      setVerifyingWalletId(null);
    }
  };

  const toggleExpand = (id: string) => {
    setExpandedJournalId(expandedJournalId === id ? null : id);
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">
            <BookOpen size={22} color="var(--accent-purple)" /> Wallets & Double-Entry Ledger
          </h1>
          <p className="page-subtitle">Pessimistic-locked customer wallets and immutable double-entry journal postings</p>
        </div>
      </div>

      {/* Customer Wallets Section */}
      <div className="card-section">
        <div className="card-section-header">
          <span className="card-section-title">
            <Users size={16} color="var(--accent-blue)" /> Customer Wallets ({wallets.length})
          </span>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-dim)' }}>
            Strict Pessimistic Locking (SELECT FOR UPDATE)
          </span>
        </div>
        <div className="table-wrapper">
          <table className="data-table">
            <thead>
              <tr>
                <th>Wallet ID</th>
                <th>Customer ID</th>
                <th>Currency</th>
                <th>Cached Balance</th>
                <th>Ledger Account ID</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {wallets.length === 0 ? (
                <tr>
                  <td colSpan={7} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-dim)' }}>
                    No customer wallets found. Create and fund one in the Sandbox Simulator!
                  </td>
                </tr>
              ) : (
                wallets.map((w) => (
                  <tr key={w.id}>
                    <td className="mono" style={{ color: 'var(--accent-blue)', fontWeight: 600 }}>
                      {w.id.substring(0, 8)}...{w.id.substring(w.id.length - 4)}
                    </td>
                    <td className="mono" style={{ fontWeight: 600 }}>{w.customerId}</td>
                    <td className="mono">{w.currency}</td>
                    <td className="mono" style={{ fontWeight: 700, color: 'var(--accent-green)' }}>
                      {w.formattedBalance}
                    </td>
                    <td className="mono" style={{ fontSize: '0.78rem', color: 'var(--text-dim)' }}>
                      {w.accountId.substring(0, 12)}...
                    </td>
                    <td style={{ color: 'var(--text-dim)', fontSize: '0.8rem' }}>
                      {new Date(w.createdAt).toLocaleTimeString()}
                    </td>
                    <td>
                      <div style={{ display: 'flex', gap: '0.4rem' }}>
                        <button
                          className="btn btn-sm"
                          onClick={() => handleVerifyWallet(w.id)}
                          disabled={verifyingWalletId === w.id}
                          title="Recalculate balance strictly from all double-entry journal lines"
                        >
                          <ShieldCheck size={12} color="var(--accent-green)" />
                          <span>{verifyingWalletId === w.id ? 'Auditing...' : 'Audit Balance'}</span>
                        </button>
                        <button
                          className="btn btn-sm btn-primary"
                          onClick={() => onOpenSandboxFunding(w.id)}
                          title="Fund test wallet"
                        >
                          <Plus size={12} />
                          <span>Fund</span>
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Double-Entry Journal Entries Section */}
      <div className="card-section">
        <div className="card-section-header">
          <span className="card-section-title">
            <Scale size={16} color="var(--accent-purple)" /> Immutable Journal Entries ({journals.length})
          </span>
          <span className="badge badge-captured" style={{ fontSize: '0.72rem' }}>
            <CheckCircle size={12} /> Σ Debits == Σ Credits
          </span>
        </div>
        <div className="table-wrapper">
          <table className="data-table">
            <thead>
              <tr>
                <th style={{ width: '40px' }}></th>
                <th>Journal ID</th>
                <th>Reference ID</th>
                <th>Total Debits</th>
                <th>Total Credits</th>
                <th>Currency</th>
                <th>Invariant Status</th>
                <th>Timestamp</th>
              </tr>
            </thead>
            <tbody>
              {journals.length === 0 ? (
                <tr>
                  <td colSpan={8} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-dim)' }}>
                    No journal entries posted yet.
                  </td>
                </tr>
              ) : (
                journals.map((j) => {
                  const isExpanded = expandedJournalId === j.id;
                  return (
                    <React.Fragment key={j.id}>
                      <tr onClick={() => toggleExpand(j.id)} style={{ cursor: 'pointer' }}>
                        <td>
                          {isExpanded ? (
                            <ChevronDown size={14} color="var(--text-dim)" />
                          ) : (
                            <ChevronRight size={14} color="var(--text-dim)" />
                          )}
                        </td>
                        <td className="mono" style={{ color: 'var(--accent-purple)' }}>
                          {j.id.substring(0, 8)}...
                        </td>
                        <td className="mono" style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                          {j.referenceId}
                        </td>
                        <td className="mono" style={{ fontWeight: 600 }}>
                          {(j.totalDebitsMinorUnits / 100).toFixed(2)}
                        </td>
                        <td className="mono" style={{ fontWeight: 600 }}>
                          {(j.totalCreditsMinorUnits / 100).toFixed(2)}
                        </td>
                        <td className="mono">{j.currency}</td>
                        <td>
                          <span className={`badge ${j.balanced ? 'badge-captured' : 'badge-failed'}`}>
                            {j.balanced ? 'BALANCED' : 'IMBALANCED'}
                          </span>
                        </td>
                        <td style={{ color: 'var(--text-dim)', fontSize: '0.8rem' }}>
                          {new Date(j.createdAt).toLocaleTimeString()}
                        </td>
                      </tr>

                      {/* Expandable Line Postings */}
                      {isExpanded && j.lines && (
                        <tr style={{ background: '#12161c' }}>
                          <td colSpan={8} style={{ padding: '0.75rem 1.5rem' }}>
                            <div style={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-dim)', marginBottom: '0.4rem' }}>
                              POSTED JOURNAL LINES FOR {j.referenceId}
                            </div>
                            <table className="data-table" style={{ background: 'transparent' }}>
                              <thead>
                                <tr>
                                  <th>Account ID</th>
                                  <th>Type</th>
                                  <th>Direction</th>
                                  <th>Amount</th>
                                  <th>Description</th>
                                </tr>
                              </thead>
                              <tbody>
                                {j.lines.map((l) => (
                                  <tr key={l.id}>
                                    <td className="mono">{l.accountId}</td>
                                    <td><span className="badge" style={{ background: '#21262d' }}>{l.accountType}</span></td>
                                    <td>
                                      <span
                                        className="badge"
                                        style={{
                                          color: l.direction === 'DEBIT' ? 'var(--accent-green)' : 'var(--accent-blue)',
                                          background: l.direction === 'DEBIT' ? 'var(--accent-green-bg)' : 'rgba(88, 166, 255, 0.12)',
                                        }}
                                      >
                                        {l.direction}
                                      </span>
                                    </td>
                                    <td className="mono" style={{ fontWeight: 600 }}>{l.formattedAmount}</td>
                                    <td style={{ color: 'var(--text-muted)' }}>{l.description}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
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
