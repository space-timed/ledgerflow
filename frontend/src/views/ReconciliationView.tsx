import React, { useState } from 'react';
import { ShieldCheck, Play, AlertTriangle, CheckCircle, Bug, ChevronDown, ChevronRight } from 'lucide-react';
import { ReconciliationRun } from '../types';
import { api } from '../services/api';

interface ReconciliationViewProps {
  runs: ReconciliationRun[];
  onRefresh: () => void;
  onNotify: (msg: string, type?: 'success' | 'error' | 'info') => void;
}

export const ReconciliationView: React.FC<ReconciliationViewProps> = ({
  runs,
  onRefresh,
  onNotify,
}) => {
  const [isRunning, setIsRunning] = useState(false);
  const [simulateAnomaly, setSimulateAnomaly] = useState(false);
  const [expandedRunId, setExpandedRunId] = useState<string | null>(null);

  const handleRunAudit = async () => {
    setIsRunning(true);
    try {
      const res = await api.triggerReconciliationRun(simulateAnomaly);
      if (res.status === 'PASSED') {
        onNotify(`Reconciliation Audit PASSED: Audited ${res.journalsChecked} journals & ${res.walletsChecked} wallets. Zero discrepancies!`, 'success');
      } else {
        onNotify(`Reconciliation Audit finished with DISCREPANCIES DETECTED: Found ${res.discrepanciesCount} anomalies!`, 'error');
      }
      onRefresh();
      setExpandedRunId(res.id);
    } catch (err: any) {
      onNotify(`Reconciliation failed: ${err.message}`, 'error');
    } finally {
      setIsRunning(false);
    }
  };

  const toggleExpand = (id: string) => {
    setExpandedRunId(expandedRunId === id ? null : id);
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">
            <ShieldCheck size={22} color="var(--accent-green)" /> Independent Financial Reconciliation
          </h1>
          <p className="page-subtitle">
            Audits double-entry invariants, wallet cached balances vs ledger history, and payment-to-ledger linkage
          </p>
        </div>
      </div>

      {/* Action Banner */}
      <div
        style={{
          background: '#161b22',
          border: '1px solid var(--border-color)',
          borderRadius: '8px',
          padding: '1.25rem',
          marginBottom: '1.5rem',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div>
          <div style={{ fontWeight: 600, fontSize: '0.95rem', color: 'var(--text-main)', marginBottom: '0.25rem' }}>
            Trigger Independent Audit Run
          </div>
          <div style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>
            Scans all journal entries, validates balance sums, asserts wallet balances, and flags anomalies.
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <label
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.45rem',
              fontSize: '0.82rem',
              cursor: 'pointer',
              color: simulateAnomaly ? 'var(--accent-yellow)' : 'var(--text-dim)',
            }}
          >
            <input
              type="checkbox"
              checked={simulateAnomaly}
              onChange={(e) => setSimulateAnomaly(e.target.checked)}
            />
            <Bug size={14} />
            <span>Simulate Anomaly (Fault Injection)</span>
          </label>

          <button
            className="btn btn-primary"
            onClick={handleRunAudit}
            disabled={isRunning}
            style={{ minWidth: '150px' }}
          >
            <Play size={14} className={isRunning ? 'animate-spin' : ''} />
            <span>{isRunning ? 'Auditing...' : 'Run Audit'}</span>
          </button>
        </div>
      </div>

      {/* Reconciliation History Table */}
      <div className="card-section">
        <div className="card-section-header">
          <span className="card-section-title">Reconciliation Run History ({runs.length})</span>
        </div>
        <div className="table-wrapper">
          <table className="data-table">
            <thead>
              <tr>
                <th style={{ width: '40px' }}></th>
                <th>Run ID</th>
                <th>Status</th>
                <th>Journals Verified</th>
                <th>Wallets Verified</th>
                <th>Discrepancies</th>
                <th>Execution Time</th>
              </tr>
            </thead>
            <tbody>
              {runs.length === 0 ? (
                <tr>
                  <td colSpan={7} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-dim)' }}>
                    No audit runs executed yet. Click "Run Audit" above to run the independent reconciler!
                  </td>
                </tr>
              ) : (
                runs.map((r) => {
                  const isExpanded = expandedRunId === r.id;
                  const isPassed = r.status === 'PASSED';
                  return (
                    <React.Fragment key={r.id}>
                      <tr onClick={() => toggleExpand(r.id)} style={{ cursor: 'pointer' }}>
                        <td>
                          {isExpanded ? (
                            <ChevronDown size={14} color="var(--text-dim)" />
                          ) : (
                            <ChevronRight size={14} color="var(--text-dim)" />
                          )}
                        </td>
                        <td className="mono" style={{ color: 'var(--accent-blue)', fontWeight: 600 }}>
                          {r.id.substring(0, 8)}...
                        </td>
                        <td>
                          <span className={`badge ${isPassed ? 'badge-passed' : 'badge-discrepancies_detected'}`}>
                            {isPassed ? <CheckCircle size={10} /> : <AlertTriangle size={10} />}
                            <span>{r.status}</span>
                          </span>
                        </td>
                        <td className="mono">{r.journalsChecked}</td>
                        <td className="mono">{r.walletsChecked}</td>
                        <td
                          className="mono"
                          style={{
                            fontWeight: 700,
                            color: r.discrepanciesCount > 0 ? 'var(--accent-red)' : 'var(--accent-green)',
                          }}
                        >
                          {r.discrepanciesCount}
                        </td>
                        <td style={{ color: 'var(--text-dim)', fontSize: '0.8rem' }}>
                          {new Date(r.createdAt).toLocaleString()}
                        </td>
                      </tr>

                      {/* Expandable Discrepancies Table */}
                      {isExpanded && (
                        <tr style={{ background: '#12161c' }}>
                          <td colSpan={7} style={{ padding: '1rem 1.5rem' }}>
                            <div style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-main)', marginBottom: '0.5rem' }}>
                              Audit Findings for Run {r.id}:
                            </div>
                            {r.discrepancies && r.discrepancies.length > 0 ? (
                              <table className="data-table" style={{ background: 'transparent' }}>
                                <thead>
                                  <tr>
                                    <th>Discrepancy Type</th>
                                    <th>Entity</th>
                                    <th>Expected</th>
                                    <th>Actual</th>
                                    <th>Description</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {r.discrepancies.map((d) => (
                                    <tr key={d.id}>
                                      <td>
                                        <span className="badge badge-discrepancies_detected" style={{ fontSize: '0.7rem' }}>
                                          {d.discrepancyType}
                                        </span>
                                      </td>
                                      <td className="mono" style={{ fontSize: '0.78rem' }}>
                                        {d.entityType}: {d.entityId.substring(0, 8)}...
                                      </td>
                                      <td className="mono" style={{ color: 'var(--accent-green)' }}>
                                        {d.expectedValue}
                                      </td>
                                      <td className="mono" style={{ color: 'var(--accent-red)' }}>
                                        {d.actualValue}
                                      </td>
                                      <td style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>
                                        {d.description}
                                      </td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            ) : (
                              <div style={{ color: 'var(--accent-green)', display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.82rem' }}>
                                <CheckCircle size={14} /> Zero discrepancies found. All double-entry invariants, wallet balances, and journal references are 100% verified.
                              </div>
                            )}
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
