package com.ledgerflow.reconciliation;

import com.ledgerflow.common.money.CurrencyCode;
import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.merchant.service.MerchantService;
import com.ledgerflow.payment.service.PaymentService;
import com.ledgerflow.reconciliation.service.ReconciliationService;
import com.ledgerflow.wallet.domain.Wallet;
import com.ledgerflow.wallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReconciliationAuditIntegrationTest {

    @Autowired
    private ReconciliationService reconciliationService;
    @Autowired
    private MerchantService merchantService;
    @Autowired
    private WalletService walletService;
    @Autowired
    private PaymentService paymentService;

    @Test
    @DisplayName("Reconciliation Engine: Healthy state audits all journals and balances with 0 discrepancies")
    void testHealthyReconciliationRun() {
        // Create baseline transactions
        MerchantService.MerchantRegistrationResult reg = merchantService.registerMerchant(
                "Recon Test Merchant",
                "recon-" + UUID.randomUUID() + "@merchant.com"
        );
        Merchant merchant = reg.merchant();

        Wallet wallet = walletService.createWallet(merchant, "cust_recon_01", CurrencyCode.INR);
        walletService.fundSandboxWallet(wallet.getId(), merchant.getId(), 5000, CurrencyCode.INR, "Recon fund", "recon_f_" + UUID.randomUUID());
        paymentService.processPayment(merchant, wallet.getId(), 2000, CurrencyCode.INR, "Recon pay", "recon_p_" + UUID.randomUUID());

        // Run full reconciliation
        ReconciliationService.ReconciliationReport report = reconciliationService.runReconciliation(false);

        assertNotNull(report.runId());
        assertEquals("PASSED", report.status());
        assertTrue(report.isHealthy());
        assertEquals(0, report.discrepanciesCount());
        assertTrue(report.checkedJournalsCount() >= 2);
        assertTrue(report.checkedWalletsCount() >= 1);
        assertTrue(report.discrepancies().isEmpty());
    }

    @Test
    @DisplayName("Reconciliation Engine: Controlled fault injection demonstrates discrepancy detection without data corruption")
    void testAnomalyDetectionFixture() {
        // Run with simulateAnomaly = true
        ReconciliationService.ReconciliationReport report = reconciliationService.runReconciliation(true);

        assertEquals("DISCREPANCIES_DETECTED", report.status());
        assertFalse(report.isHealthy());
        assertTrue(report.discrepanciesCount() >= 1);

        boolean foundSimulatedAnomaly = report.discrepancies().stream()
                .anyMatch(d -> d.discrepancyType().equals("SIMULATED_TEST_ANOMALY"));
        assertTrue(foundSimulatedAnomaly, "Audit must report injected anomaly");
    }
}
