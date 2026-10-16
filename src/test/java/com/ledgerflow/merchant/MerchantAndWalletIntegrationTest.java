package com.ledgerflow.merchant;

import com.ledgerflow.common.error.BusinessException;
import com.ledgerflow.common.error.ErrorCode;
import com.ledgerflow.common.money.CurrencyCode;
import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.merchant.service.ApiKeyService;
import com.ledgerflow.merchant.service.MerchantService;
import com.ledgerflow.wallet.domain.Wallet;
import com.ledgerflow.wallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MerchantAndWalletIntegrationTest {

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private WalletService walletService;

    @Test
    @DisplayName("Merchant onboarding, API key auth, customer wallet creation and sandbox funding flow")
    void testEndToEndMerchantWalletAccountingFlow() {
        // 1. Onboard Merchant
        MerchantService.MerchantRegistrationResult reg = merchantService.registerMerchant("Stripe India Test", "onboarding@stripe-test.com");
        assertNotNull(reg.merchant().getId());
        assertNotNull(reg.apiKey().rawApiKey());
        assertTrue(reg.apiKey().rawApiKey().startsWith("lf_live_"));

        // 2. Authenticate API Key
        Merchant authenticated = apiKeyService.authenticate(reg.apiKey().rawApiKey());
        assertEquals(reg.merchant().getId(), authenticated.getId());

        // 3. Invalid API Key should be rejected
        BusinessException authEx = assertThrows(BusinessException.class, () ->
                apiKeyService.authenticate("lf_live_invalid_token_1234567890")
        );
        assertEquals(ErrorCode.UNAUTHORIZED, authEx.getErrorCode());

        // 4. Create Customer Wallet in INR
        Wallet wallet = walletService.createWallet(authenticated, "cust_alpha_01", CurrencyCode.INR);
        assertNotNull(wallet.getId());
        assertEquals(0, wallet.getBalanceMinorUnits());
        assertEquals(CurrencyCode.INR, wallet.getCurrency());
        assertNotNull(wallet.getAccount());

        // 5. Creating duplicate wallet for same customer & currency is rejected
        BusinessException dupEx = assertThrows(BusinessException.class, () ->
                walletService.createWallet(authenticated, "cust_alpha_01", CurrencyCode.INR)
        );
        assertEquals(ErrorCode.INVALID_PAYLOAD, dupEx.getErrorCode());

        // 6. Fund Sandbox Wallet with 10,000 INR minor units (₹100.00)
        WalletService.SandboxFundingResult fundResult = walletService.fundSandboxWallet(
                wallet.getId(),
                authenticated.getId(),
                10000,
                CurrencyCode.INR,
                "Initial test wallet deposit",
                "fund_key_001"
        );

        assertEquals(10000, fundResult.creditedAmount());
        assertEquals(10000, fundResult.newBalanceMinorUnits());
        assertEquals("₹ 100.00", fundResult.formattedBalance());
        assertNotNull(fundResult.journalEntryId());

        // 7. Verify Wallet Balance against Ledger Invariant
        WalletService.WalletBalanceResponse balance = walletService.getWalletBalance(wallet.getId(), authenticated.getId());
        assertEquals(10000, balance.balanceMinorUnits());
        assertTrue(balance.ledgerVerified(), "Wallet cached balance must strictly agree with posted double-entry journal balance");
    }
}
