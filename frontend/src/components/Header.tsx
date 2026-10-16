import React, { useState } from 'react';
import { Layers, Activity, Key, RefreshCw, CheckCircle2, AlertCircle } from 'lucide-react';
import { api } from '../services/api';

interface HeaderProps {
  online: boolean;
  onRefresh: () => void;
  isRefreshing: boolean;
  autoRefresh: boolean;
  setAutoRefresh: (val: boolean) => void;
  onNotify: (msg: string, type?: 'success' | 'error' | 'info') => void;
}

export const Header: React.FC<HeaderProps> = ({
  online,
  onRefresh,
  isRefreshing,
  autoRefresh,
  setAutoRefresh,
  onNotify,
}) => {
  const [showKeyModal, setShowKeyModal] = useState(false);
  const [inputKey, setInputKey] = useState(api.getApiKey());

  const handleSaveKey = () => {
    api.setApiKey(inputKey);
    setShowKeyModal(false);
    onNotify('API Key updated successfully', 'success');
    onRefresh();
  };

  return (
    <>
      <header className="top-navbar">
        <div className="logo-badge">
          <Layers className="logo-icon" size={22} />
          <span>LedgerFlow</span>
          <span className="product-tag">Engine</span>
        </div>

        <div className="nav-actions">
          <div className={`status-pill ${online ? 'online' : 'offline'}`}>
            <span className="pulse-dot" />
            <span>{online ? 'Core Engine Online (Port 8080)' : 'Backend Offline'}</span>
          </div>

          <button
            className="btn btn-sm"
            onClick={() => setAutoRefresh(!autoRefresh)}
            title="Toggle 5s background polling"
            style={{ color: autoRefresh ? 'var(--accent-green)' : 'var(--text-muted)' }}
          >
            <Activity size={14} />
            <span>Live {autoRefresh ? 'ON' : 'OFF'}</span>
          </button>

          <button
            className="btn btn-sm"
            onClick={onRefresh}
            disabled={isRefreshing}
            title="Manual sync"
          >
            <RefreshCw size={14} className={isRefreshing ? 'animate-spin' : ''} />
            <span>Sync</span>
          </button>

          <button
            className="btn btn-sm"
            onClick={() => setShowKeyModal(true)}
            style={{ borderColor: api.getApiKey() ? 'var(--accent-green)' : 'var(--border-color)' }}
          >
            <Key size={14} />
            <span>{api.getApiKey() ? 'API Key Configured' : 'Set API Key'}</span>
          </button>
        </div>
      </header>

      {showKeyModal && (
        <div className="modal-overlay" onClick={() => setShowKeyModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '480px' }}>
            <div className="modal-header">
              <h3 style={{ fontSize: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Key size={16} color="var(--accent-blue)" /> Configure Merchant API Key
              </h3>
            </div>
            <div className="modal-body">
              <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                Requests to authenticated endpoints (payments, wallets, webhooks) require a valid SHA-256 hashed API key.
                You can create a new key via the Sandbox tab or paste an existing one here.
              </p>
              <div className="form-group">
                <label className="form-label">Merchant Secret Key (lf_sec_...)</label>
                <input
                  type="password"
                  className="form-input mono"
                  value={inputKey}
                  onChange={(e) => setInputKey(e.target.value)}
                  placeholder="lf_sec_xxxxxxxxxxxxxxxxxxxxxx"
                  autoFocus
                />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '0.5rem' }}>
                <button className="btn" onClick={() => setShowKeyModal(false)}>
                  Cancel
                </button>
                <button className="btn btn-primary" onClick={handleSaveKey}>
                  Save Key
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
};
