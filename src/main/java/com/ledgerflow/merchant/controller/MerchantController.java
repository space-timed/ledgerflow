package com.ledgerflow.merchant.controller;

import com.ledgerflow.common.security.MerchantContext;
import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.merchant.service.ApiKeyService;
import com.ledgerflow.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchants")
@Tag(name = "Merchants", description = "Merchant onboarding and API key management")
public class MerchantController {

    private final MerchantService merchantService;
    private final ApiKeyService apiKeyService;

    public MerchantController(MerchantService merchantService, ApiKeyService apiKeyService) {
        this.merchantService = merchantService;
        this.apiKeyService = apiKeyService;
    }

    public record RegisterMerchantRequest(
            @NotBlank(message = "Merchant name is required") String name,
            @NotBlank(message = "Email is required") @Email(message = "Valid email is required") String email
    ) {}

    public record ApiKeyResponse(
            UUID id,
            String apiKey,
            String keyPrefix,
            String name,
            Instant createdAt
    ) {}

    public record MerchantResponse(
            UUID id,
            String name,
            String email,
            String status,
            ApiKeyResponse initialApiKey,
            Instant createdAt
    ) {}

    public record CreateApiKeyRequest(
            String name
    ) {}

    @PostMapping
    @Operation(summary = "Onboard a new sandbox merchant", description = "Creates a merchant and automatically provisions initial API keys and currency settlement accounts.")
    public ResponseEntity<MerchantResponse> registerMerchant(@Valid @RequestBody RegisterMerchantRequest request) {
        MerchantService.MerchantRegistrationResult result = merchantService.registerMerchant(request.name(), request.email());

        ApiKeyResponse keyResponse = new ApiKeyResponse(
                result.apiKey().id(),
                result.apiKey().rawApiKey(),
                result.apiKey().keyPrefix(),
                result.apiKey().name(),
                result.apiKey().createdAt()
        );

        MerchantResponse response = new MerchantResponse(
                result.merchant().getId(),
                result.merchant().getName(),
                result.merchant().getEmail(),
                result.merchant().getStatus().name(),
                keyResponse,
                result.merchant().getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/keys")
    @Operation(summary = "Generate a new API key for merchant", description = "Generates a new secret API key. The raw key is only returned once upon creation.")
    public ResponseEntity<ApiKeyResponse> createApiKey(@PathVariable UUID id,
                                                       @RequestBody(required = false) CreateApiKeyRequest request) {
        Merchant merchant = merchantService.getMerchant(id);
        String keyName = (request != null) ? request.name() : "Generated Key";
        ApiKeyService.GeneratedApiKey key = apiKeyService.generateApiKey(merchant, keyName);

        ApiKeyResponse response = new ApiKeyResponse(
                key.id(),
                key.rawApiKey(),
                key.keyPrefix(),
                key.name(),
                key.createdAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated merchant details")
    public ResponseEntity<MerchantResponse> getCurrentMerchant() {
        Merchant merchant = MerchantContext.getMerchant();
        MerchantResponse response = new MerchantResponse(
                merchant.getId(),
                merchant.getName(),
                merchant.getEmail(),
                merchant.getStatus().name(),
                null,
                merchant.getCreatedAt()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get merchant by ID")
    public ResponseEntity<MerchantResponse> getMerchantById(@PathVariable UUID id) {
        Merchant merchant = merchantService.getMerchant(id);
        MerchantResponse response = new MerchantResponse(
                merchant.getId(),
                merchant.getName(),
                merchant.getEmail(),
                merchant.getStatus().name(),
                null,
                merchant.getCreatedAt()
        );
        return ResponseEntity.ok(response);
    }
}
