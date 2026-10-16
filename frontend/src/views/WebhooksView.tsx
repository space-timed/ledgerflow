import React, { useState, useEffect } from 'react';
import { Webhook, Send, RotateCw, CheckCircle, ShieldCheck, Trash2, Key } from 'lucide-react';
import { WebhookSubscription, WebhookDelivery, ReceivedWebhookEvent } from '../types';
import { api } from '../services/api';

interface WebhooksViewProps {
  subscriptions: WebhookSubscription[];
  deliveries: WebhookDelivery[];
  onRefresh: () => void;
  onNotify: (msg: string, type?: 'success' | 'error' | 'info') => void;
}

export const WebhooksView: React.FC<WebhooksViewProps> = ({
  subscriptions,
  deliveries,
  onRefresh,
  onNotify,
}) => {
  const [mockEvents, setMockEvents] = useState<ReceivedWebhookEvent[]>([]);
  const [redeliveringId, setRedeliveringId] = useState<string | null>(null);

  const fetchMockEvents = async () => {
    try {
      const events = await api.getMockRecentDeliveries();
      setMockEvents(events);
    } catch (err) {
      console.error('Failed to load mock deliveries', err);
    }
  };

  useEffect(() => {
    fetchMockEvents();
    const interval = setInterval(fetchMockEvents, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleRedeliver = async (deliveryId: string) => {
    setRedeliveringId(deliveryId);
    try {
      await api.redeliverWebhook(deliveryId);
      onNotify('Manual redelivery initiated successfully!', 'success');
      onRefresh();
      fetchMockEvents();
    } catch (err: any) {
      onNotify(`Redelivery failed: ${err.message}`, 'error');
    } finally {
      setRedeliveringId(null);
    }
  };

  const handleClearMock = async () => {
    try {
      await api.clearMockRecentDeliveries();
      setMockEvents([]);
      onNotify('Cleared mock receiver memory log', 'info');
    } catch (err: any) {
      onNotify(`Clear failed: ${err.message}`, 'error');
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">
            <Webhook size={22} color="var(--accent-blue)" /> Webhook Delivery & HMAC Signatures
          </h1>
          <p className="page-subtitle">
            Reliable event-to-webhook routing, timestamped HMAC-SHA256 payload verification, and manual redelivery
          </p>
        </div>
      </div>

      {/* Two Columns: Subscriptions & Mock Receiver Feed */}
      <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: '1.5rem', marginBottom: '1.5rem' }}>
        {/* Subscriptions */}
        <div className="card-section" style={{ marginBottom: 0 }}>
          <div className="card-section-header">
            <span className="card-section-title">
              <Key size={16} color="var(--accent-green)" /> Webhook Subscriptions ({subscriptions.length})
            </span>
          </div>
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Target URL</th>
                  <th>Subscribed Events</th>
                  <th>Status</th>
                  <th>Created</th>
                </tr>
              </thead>
              <tbody>
                {subscriptions.length === 0 ? (
                  <tr>
                    <td colSpan={4} style={{ textAlign: 'center', padding: '1.5rem', color: 'var(--text-dim)' }}>
                      No subscriptions. Create one via Sandbox or API!
                    </td>
                  </tr>
                ) : (
                  subscriptions.map((s) => (
                    <tr key={s.id}>
                      <td className="mono" style={{ fontSize: '0.8rem', color: 'var(--accent-blue)' }}>
                        {s.targetUrl}
                      </td>
                      <td>
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.25rem' }}>
                          {s.events.map((ev) => (
                            <span key={ev} className="badge" style={{ background: '#21262d', fontSize: '0.7rem' }}>
                              {ev}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td>
                        <span className="badge badge-active">{s.status}</span>
                      </td>
                      <td style={{ color: 'var(--text-dim)', fontSize: '0.8rem' }}>
                        {new Date(s.createdAt).toLocaleTimeString()}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Live Mock Receiver Feed */}
        <div className="card-section" style={{ marginBottom: 0 }}>
          <div className="card-section-header">
            <span className="card-section-title">
              <ShieldCheck size={16} color="var(--accent-green)" /> Mock Webhook Receiver Live Stream
            </span>
            {mockEvents.length > 0 && (
              <button className="btn btn-sm btn-danger" onClick={handleClearMock} style={{ padding: '0.2rem 0.5rem' }}>
                <Trash2 size={12} /> Clear
              </button>
            )}
          </div>
          <div style={{ padding: '0.75rem', maxHeight: '280px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '0.65rem' }}>
            {mockEvents.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-dim)', fontSize: '0.82rem' }}>
                No events received yet. Point a subscription to <br />
                <code className="mono" style={{ color: 'var(--accent-blue)' }}>http://localhost:8080/api/v1/mock/webhook-receiver</code>
              </div>
            ) : (
              mockEvents.map((ev, idx) => (
                <div
                  key={idx}
                  style={{
                    background: '#12161c',
                    border: '1px solid var(--border-color)',
                    borderRadius: '6px',
                    padding: '0.65rem',
                    fontSize: '0.78rem',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.35rem' }}>
                    <span className="badge badge-captured" style={{ fontSize: '0.68rem' }}>
                      <CheckCircle size={10} /> HMAC SIGNATURE VERIFIED
                    </span>
                    <span style={{ color: 'var(--text-dim)' }}>{new Date(ev.receivedAt).toLocaleTimeString()}</span>
                  </div>
                  <div className="mono" style={{ color: 'var(--text-dim)', marginBottom: '0.25rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    Header: {ev.signature}
                  </div>
                  <pre className="mono" style={{ color: '#7ee787', margin: 0, fontSize: '0.75rem', whiteSpace: 'pre-wrap' }}>
                    {ev.payload}
                  </pre>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* Delivery Attempts Table */}
      <div className="card-section">
        <div className="card-section-header">
          <span className="card-section-title">
            <Send size={16} color="var(--accent-yellow)" /> Delivery Attempts Log ({deliveries.length})
          </span>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-dim)' }}>
            Exponential Backoff with Jitter
          </span>
        </div>
        <div className="table-wrapper">
          <table className="data-table">
            <thead>
              <tr>
                <th>Delivery ID</th>
                <th>Event Type</th>
                <th>Status</th>
                <th>Attempts</th>
                <th>HTTP Status</th>
                <th>Next Retry / Timestamp</th>
                <th>Manual Redelivery</th>
              </tr>
            </thead>
            <tbody>
              {deliveries.length === 0 ? (
                <tr>
                  <td colSpan={7} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-dim)' }}>
                    No webhook delivery attempts recorded.
                  </td>
                </tr>
              ) : (
                deliveries.map((d) => {
                  const statusClass = `badge-${d.status.toLowerCase()}`;
                  return (
                    <tr key={d.id}>
                      <td className="mono" style={{ color: 'var(--accent-blue)' }}>
                        {d.id.substring(0, 8)}...
                      </td>
                      <td className="mono" style={{ fontWeight: 600 }}>{d.eventType}</td>
                      <td>
                        <span className={`badge ${statusClass}`}>{d.status}</span>
                      </td>
                      <td className="mono">{d.attemptCount}</td>
                      <td className="mono" style={{ color: d.responseStatus === 200 ? 'var(--accent-green)' : 'var(--accent-red)' }}>
                        {d.responseStatus || '—'}
                      </td>
                      <td style={{ color: 'var(--text-dim)', fontSize: '0.8rem' }}>
                        {d.deliveredAt
                          ? new Date(d.deliveredAt).toLocaleTimeString()
                          : d.nextRetryAt
                          ? `Retry: ${new Date(d.nextRetryAt).toLocaleTimeString()}`
                          : new Date(d.createdAt).toLocaleTimeString()}
                      </td>
                      <td>
                        <button
                          className="btn btn-sm"
                          onClick={() => handleRedeliver(d.id)}
                          disabled={redeliveringId === d.id}
                        >
                          <RotateCw size={12} className={redeliveringId === d.id ? 'animate-spin' : ''} />
                          <span>{redeliveringId === d.id ? 'Redelivering...' : 'Redeliver'}</span>
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
