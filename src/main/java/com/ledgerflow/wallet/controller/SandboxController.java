package com.ledgerflow.wallet.controller;

import com.ledgerflow.common.money.CurrencyCode;
import com.ledgerflow.common.security.MerchantContext;
import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sandbox")
@Tag(name = "Sandbox", description = "Test utilities and sandbox wallet funding")
public class SandboxController {

    private final WalletService walletService;

    public SandboxController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/funding")
    @Operation(summary = "Fund a sandbox wallet via double-entry journal", description = "Credits the customer wallet from the system clearing account using balanced double-entry accounting.")
    public ResponseEntity<WalletService.SandboxFundingResult> fundWallet(
            @Parameter(description = "Optional idempotency key")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WalletController.FundWalletRequest request) {

        Merchant merchant = MerchantContext.getMerchant();
        CurrencyCode currency = CurrencyCode.fromString(request.currency());

        WalletService.SandboxFundingResult result = walletService.fundSandboxWallet(
                request.walletId(),
                merchant.getId(),
                request.amountMinorUnits(),
                currency,
                request.description(),
                idempotencyKey
        );

        return ResponseEntity.ok(result);
    }
}
