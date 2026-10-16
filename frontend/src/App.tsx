import React, { useState, useEffect, useCallback } from 'react';
import { Header } from './components/Header';
import { Sidebar, TabType } from './components/Sidebar';
import { PaymentModal } from './components/PaymentModal';
import { OverviewView } from './views/OverviewView';
import { PaymentsView } from './views/PaymentsView';
import { WalletsLedgerView } from './views/WalletsLedgerView';
import { OutboxView } from './views/OutboxView';
import { WebhooksView } from './views/WebhooksView';
import { ReconciliationView } from './views/ReconciliationView';
import { SandboxView } from './views/SandboxView';
import { api } from './services/api';
import {
  Merchant,
  Wallet,
  Payment,
  JournalEntry,
  OutboxEvent,
  OutboxStats,
  WebhookSubscription,
  WebhookDelivery,
  ReconciliationRun,
} from './types';
import { CheckCircle2, AlertCircle, Info } from 'lucide-react';

interface ToastItem {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
}

export function App() {
  const [currentTab, setCurrentTab] = useState<TabType>('overview');
  const [online, setOnline] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(true);

  // Domain data
  const [merchants, setMerchants] = useState<Merchant[]>([]);
  const [wallets, setWallets] = useState<Wallet[]>([]);
  const [payments, setPayments] = useState<Payment[]>([]);
  const [journals, setJournals] = useState<JournalEntry[]>([]);
  const [outboxEvents, setOutboxEvents] = useState<OutboxEvent[]>([]);
  const [outboxStats, setOutboxStats] = useState<OutboxStats | null>(null);
  const [subscriptions, setSubscriptions] = useState<WebhookSubscription[]>([]);
  const [deliveries, setDeliveries] = useState<WebhookDelivery[]>([]);
  const [reconciliationRuns, setReconciliationRuns] = useState<ReconciliationRun[]>([]);

  // Selected payment for inspector modal
  const [selectedPayment, setSelectedPayment] = useState<Payment | null>(null);

  // Selected wallet for funding redirect
  const [fundWalletTargetId, setFundWalletTargetId] = useState<string | undefined>(undefined);

  // Toast notifications
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const notify = useCallback((message: string, type: 'success' | 'error' | 'info' = 'info') => {
    const id = Math.random().toString(36).substring(2, 9);
    setToasts((prev) => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 4500);
  }, []);

  const refreshData = useCallback(async () => {
    setIsRefreshing(true);
    try {
      const health = await api.checkHealth();
      setOnline(health.status === 'UP');

      // Fetch non-authenticated resources
      const [reconList, outboxList, oStats] = await Promise.allSettled([
        api.listReconciliationRuns(),
        api.getOutboxEvents(),
        api.getOutboxStats(),
      ]);

      if (reconList.status === 'fulfilled') setReconciliationRuns(reconList.value);
      if (outboxList.status === 'fulfilled') setOutboxEvents(outboxList.value);
      if (oStats.status === 'fulfilled') setOutboxStats(oStats.value);

      // Authenticated resources (if API key is present)
      if (api.getApiKey()) {
        const [wList, pList, jList, sList, dList] = await Promise.allSettled([
          api.listWallets(),
          api.listPayments(),
          api.listJournals(),
          api.listWebhookSubscriptions(),
          api.listWebhookDeliveries(),
        ]);

        if (wList.status === 'fulfilled') setWallets(wList.value);
        if (pList.status === 'fulfilled') setPayments(pList.value);
        if (jList.status === 'fulfilled') setJournals(jList.value);
        if (sList.status === 'fulfilled') setSubscriptions(sList.value);
        if (dList.status === 'fulfilled') setDeliveries(dList.value);
      }
    } catch (err: any) {
      console.warn('Refresh error:', err.message);
    } finally {
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    refreshData();
  }, [refreshData]);

  useEffect(() => {
    if (!autoRefresh) return;
    const interval = setInterval(refreshData, 5000);
    return () => clearInterval(interval);
  }, [autoRefresh, refreshData]);

  const handleOpenSandboxFunding = (walletId: string) => {
    setFundWalletTargetId(walletId);
    setCurrentTab('sandbox');
  };

  const counts = {
    payments: payments.length,
    wallets: wallets.length,
    outboxPending: outboxStats?.pendingCount || 0,
    webhookDeliveries: deliveries.length,
    reconciliationRuns: reconciliationRuns.length,
  };

  return (
    <div className="app-container">
      <Header
        online={online}
        onRefresh={refreshData}
        isRefreshing={isRefreshing}
        autoRefresh={autoRefresh}
        setAutoRefresh={setAutoRefresh}
        onNotify={notify}
      />

      <div className="main-body">
        <Sidebar currentTab={currentTab} onTabChange={setCurrentTab} counts={counts} />

        <main className="content-area">
          {currentTab === 'overview' && (
            <OverviewView
              payments={payments}
              wallets={wallets}
              journals={journals}
              outboxStats={outboxStats}
              latestReconciliation={reconciliationRuns[0] || null}
              onSelectPayment={setSelectedPayment}
            />
          )}

          {currentTab === 'payments' && (
            <PaymentsView
              payments={payments}
              onSelectPayment={setSelectedPayment}
              onRefresh={refreshData}
            />
          )}

          {currentTab === 'wallets' && (
            <WalletsLedgerView
              wallets={wallets}
              journals={journals}
              onRefresh={refreshData}
              onNotify={notify}
              onOpenSandboxFunding={handleOpenSandboxFunding}
            />
          )}

          {currentTab === 'outbox' && (
            <OutboxView
              events={outboxEvents}
              stats={outboxStats}
              onRefresh={refreshData}
            />
          )}

          {currentTab === 'webhooks' && (
            <WebhooksView
              subscriptions={subscriptions}
              deliveries={deliveries}
              onRefresh={refreshData}
              onNotify={notify}
            />
          )}

          {currentTab === 'reconciliation' && (
            <ReconciliationView
              runs={reconciliationRuns}
              onRefresh={refreshData}
              onNotify={notify}
            />
          )}

          {currentTab === 'sandbox' && (
            <SandboxView
              merchants={merchants}
              wallets={wallets}
              onRefresh={refreshData}
              onNotify={notify}
              defaultFundWalletId={fundWalletTargetId}
            />
          )}
        </main>
      </div>

      {/* Payment Detail Modal */}
      {selectedPayment && (
        <PaymentModal
          payment={selectedPayment}
          onClose={() => setSelectedPayment(null)}
          onRefresh={refreshData}
          onNotify={notify}
        />
      )}

      {/* Toasts */}
      <div className="toast-container">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.type}`}>
            {t.type === 'success' && <CheckCircle2 size={16} />}
            {t.type === 'error' && <AlertCircle size={16} />}
            {t.type === 'info' && <Info size={16} />}
            <span>{t.message}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

export default App;
