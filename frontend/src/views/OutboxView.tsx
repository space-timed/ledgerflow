import React, { useState } from 'react';
import { Send, CheckCircle, Clock, AlertTriangle, Eye, Layers } from 'lucide-react';
import { OutboxEvent, OutboxStats } from '../types';

interface OutboxViewProps {
  events: OutboxEvent[];
  stats: OutboxStats | null;
  onRefresh: () => void;
}

export const OutboxView: React.FC<OutboxViewProps> = ({ events, stats, onRefresh }) => {
  const [selectedPayload, setSelectedPayload] = useState<{ id: string; payload: string } | null>(null);

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">
            <Send size={22} color="var(--accent-yellow)" /> Transactional Outbox & Kafka
          </h1>
          <p className="page-subtitle">Guaranteed dual-write safety: database transaction stages domain events into outbox</p>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="kpi-grid">
        <div className="kpi-card">
          <div className="kpi-header">
            <span>PENDING DRAIN</span>
            <Clock size={16} color="var(--accent-yellow)" />
          </div>
          <div className="kpi-value" style={{ color: 'var(--accent-yellow)' }}>
            {stats?.pendingCount ?? 0}
          </div>
          <div className="kpi-subtext">Waiting for background poller drain</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span>PUBLISHED TO KAFKA</span>
            <CheckCircle size={16} color="var(--accent-green)" />
          </div>
          <div className="kpi-value" style={{ color: 'var(--accent-green)' }}>
            {stats?.publishedCount ?? 0}
          </div>
          <div className="kpi-subtext">Delivered with partition key routing</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span>FAILED ATTEMPTS</span>
            <AlertTriangle size={16} color="var(--accent-red)" />
          </div>
          <div className="kpi-value" style={{ color: 'var(--accent-red)' }}>
            {stats?.failedCount ?? 0}
          </div>
          <div className="kpi-subtext">Dead-letter queue candidate count</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span>TOTAL AGGREGATE EVENTS</span>
            <Layers size={16} color="var(--accent-blue)" />
          </div>
          <div className="kpi-value">{stats?.totalCount ?? events.length}</div>
          <div className="kpi-subtext">Events staged in PostgreSQL</div>
        </div>
      </div>

      {/* Outbox Table */}
      <div className="card-section">
        <div className="card-section-header">
          <span className="card-section-title">Transactional Outbox Queue</span>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-dim)' }}>
            SELECT ... FOR UPDATE SKIP LOCKED
          </span>
        </div>
        <div className="table-wrapper">
          <table className="data-table">
            <thead>
              <tr>
                <th>Event ID</th>
                <th>Topic</th>
                <th>Event Type</th>
                <th>Partition Key</th>
                <th>Status</th>
                <th>Attempts</th>
                <th>Staged Time</th>
                <th>Payload</th>
              </tr>
            </thead>
            <tbody>
              {events.length === 0 ? (
                <tr>
                  <td colSpan={8} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-dim)' }}>
                    No events in outbox. Process payments or wallet funding to generate events!
                  </td>
                </tr>
              ) : (
                events.map((e) => {
                  const statusClass = `badge-${e.status.toLowerCase()}`;
                  return (
                    <tr key={e.id}>
                      <td className="mono" style={{ color: 'var(--accent-blue)' }}>
                        {e.id.substring(0, 8)}...
                      </td>
                      <td className="mono" style={{ color: 'var(--accent-purple)' }}>{e.topic}</td>
                      <td className="mono" style={{ fontWeight: 600 }}>{e.eventType}</td>
                      <td className="mono" style={{ fontSize: '0.78rem', color: 'var(--text-dim)' }}>
                        {e.partitionKey ? e.partitionKey.substring(0, 12) : '—'}...
                      </td>
                      <td>
                        <span className={`badge ${statusClass}`}>{e.status}</span>
                      </td>
                      <td className="mono">{e.retryCount}</td>
                      <td style={{ color: 'var(--text-dim)', fontSize: '0.8rem' }}>
                        {new Date(e.createdAt).toLocaleTimeString()}
                      </td>
                      <td>
                        <button
                          className="btn btn-sm"
                          onClick={() => setSelectedPayload({ id: e.id, payload: e.payload })}
                        >
                          <Eye size={12} />
                          <span>View JSON</span>
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

      {/* JSON Payload Inspection Modal */}
      {selectedPayload && (
        <div className="modal-overlay" onClick={() => setSelectedPayload(null)}>
          <div className="modal-content" onClick={(ev) => ev.stopPropagation()} style={{ maxWidth: '600px' }}>
            <div className="modal-header">
              <h3 style={{ fontSize: '1rem', fontWeight: 600 }}>Event Payload: {selectedPayload.id}</h3>
              <button className="btn btn-sm" onClick={() => setSelectedPayload(null)}>✕</button>
            </div>
            <div className="modal-body">
              <pre
                className="mono"
                style={{
                  background: '#090d13',
                  padding: '1rem',
                  borderRadius: '6px',
                  border: '1px solid var(--border-color)',
                  overflowX: 'auto',
                  fontSize: '0.82rem',
                  lineHeight: '1.4',
                  color: '#7ee787',
                }}
              >
                {(() => {
                  try {
                    return JSON.stringify(JSON.parse(selectedPayload.payload), null, 2);
                  } catch {
                    return selectedPayload.payload;
                  }
                })()}
              </pre>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
