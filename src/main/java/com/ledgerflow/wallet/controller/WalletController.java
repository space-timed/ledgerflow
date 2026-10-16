package com.ledgerflow.wallet.controller;

import com.ledgerflow.common.money.CurrencyCode;
import com.ledgerflow.common.security.MerchantContext;
import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.wallet.domain.Wallet;
import com.ledgerflow.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@Tag(name = "Wallets", description = "Customer wallet creation, balance inquiries, and test funding")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    public record CreateWalletRequest(
            @NotBlank(message = "Customer ID is required") String customerId,
            @NotBlank(message = "Currency code is required") String currency
    ) {}

    public record WalletResponse(
            UUID id,
            String customerId,
            UUID accountId,
            String currency,
            long balanceMinorUnits,
            String formattedBalance,
            Instant createdAt
    ) {}

    public record FundWalletRequest(
            @NotNull(message = "Wallet ID is required") UUID walletId,
            @Min(value = 1, message = "Amount must be at least 1 minor unit") long amountMinorUnits,
            @NotBlank(message = "Currency is required") String currency,
            String description
    ) {}

    @PostMapping
    @Operation(summary = "Create a customer wallet", description = "Creates a customer wallet and links it to a dedicated double-entry asset account.")
    public ResponseEntity<WalletResponse> createWallet(@Valid @RequestBody CreateWalletRequest request) {
        Merchant merchant = MerchantContext.getMerchant();
        CurrencyCode currency = CurrencyCode.fromString(request.currency());

        Wallet wallet = walletService.createWallet(merchant, request.customerId(), currency);

        WalletResponse response = new WalletResponse(
                wallet.getId(),
                wallet.getCustomerId(),
                wallet.getAccount().getId(),
                wallet.getCurrency().name(),
                wallet.getBalanceMinorUnits(),
                wallet.getCurrency().formatMinorUnits(wallet.getBalanceMinorUnits()),
                wallet.getCreatedAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List customer wallets for merchant")
    public ResponseEntity<java.util.List<WalletResponse>> listWallets() {
        Merchant merchant = MerchantContext.getMerchant();
        java.util.List<Wallet> wallets = walletService.listWallets(merchant.getId());
        java.util.List<WalletResponse> response = wallets.stream()
                .map(w -> new WalletResponse(
                        w.getId(),
                        w.getCustomerId(),
                        w.getAccount().getId(),
                        w.getCurrency().name(),
                        w.getBalanceMinorUnits(),
                        w.getCurrency().formatMinorUnits(w.getBalanceMinorUnits()),
                        w.getCreatedAt()
                ))
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get wallet details by ID")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable UUID id) {
        Merchant merchant = MerchantContext.getMerchant();
        Wallet wallet = walletService.getWallet(id, merchant.getId());

        WalletResponse response = new WalletResponse(
                wallet.getId(),
                wallet.getCustomerId(),
                wallet.getAccount().getId(),
                wallet.getCurrency().name(),
                wallet.getBalanceMinorUnits(),
                wallet.getCurrency().formatMinorUnits(wallet.getBalanceMinorUnits()),
                wallet.getCreatedAt()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Get wallet balance with ledger verification", description = "Calculates exact balance from double-entry journal postings and asserts against cached balance.")
    public ResponseEntity<WalletService.WalletBalanceResponse> getWalletBalance(@PathVariable UUID id) {
        Merchant merchant = MerchantContext.getMerchant();
        WalletService.WalletBalanceResponse balance = walletService.getWalletBalance(id, merchant.getId());
        return ResponseEntity.ok(balance);
    }
}
