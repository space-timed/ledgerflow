import React from 'react';
import {
  LayoutDashboard,
  CreditCard,
  BookOpen,
  Send,
  Webhook,
  ShieldCheck,
  Terminal,
} from 'lucide-react';

export type TabType =
  | 'overview'
  | 'payments'
  | 'wallets'
  | 'outbox'
  | 'webhooks'
  | 'reconciliation'
  | 'sandbox';

interface SidebarProps {
  currentTab: TabType;
  onTabChange: (tab: TabType) => void;
  counts: {
    payments: number;
    wallets: number;
    outboxPending: number;
    webhookDeliveries: number;
    reconciliationRuns: number;
  };
}

export const Sidebar: React.FC<SidebarProps> = ({ currentTab, onTabChange, counts }) => {
  const navItems: { id: TabType; label: string; icon: React.ReactNode; badge?: number }[] = [
    {
      id: 'overview',
      label: 'Overview',
      icon: <LayoutDashboard size={18} />,
    },
    {
      id: 'payments',
      label: 'Payments & Refunds',
      icon: <CreditCard size={18} />,
      badge: counts.payments,
    },
    {
      id: 'wallets',
      label: 'Wallets & Ledger',
      icon: <BookOpen size={18} />,
      badge: counts.wallets,
    },
    {
      id: 'outbox',
      label: 'Outbox & Kafka',
      icon: <Send size={18} />,
      badge: counts.outboxPending,
    },
    {
      id: 'webhooks',
      label: 'Webhooks',
      icon: <Webhook size={18} />,
      badge: counts.webhookDeliveries,
    },
    {
      id: 'reconciliation',
      label: 'Reconciliation',
      icon: <ShieldCheck size={18} />,
      badge: counts.reconciliationRuns,
    },
    {
      id: 'sandbox',
      label: 'Sandbox Simulator',
      icon: <Terminal size={18} />,
    },
  ];

  return (
    <aside className="sidebar">
      {navItems.map((item) => {
        const isActive = currentTab === item.id;
        return (
          <button
            key={item.id}
            className={`nav-item ${isActive ? 'active' : ''}`}
            onClick={() => onTabChange(item.id)}
          >
            {item.icon}
            <span>{item.label}</span>
            {item.badge !== undefined && item.badge > 0 && (
              <span className="nav-badge">{item.badge}</span>
            )}
          </button>
        );
      })}

      <div style={{ marginTop: 'auto', padding: '0.75rem', fontSize: '0.75rem', color: 'var(--text-dim)', borderTop: '1px solid var(--border-color)' }}>
        <div>PostgreSQL 16: Port 5433</div>
        <div>Kafka 4.3.1: Port 9092</div>
        <div>Redis 7: Port 6379</div>
      </div>
    </aside>
  );
};
