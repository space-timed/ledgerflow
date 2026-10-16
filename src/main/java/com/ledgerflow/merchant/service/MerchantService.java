package com.ledgerflow.merchant.service;

import com.ledgerflow.common.error.BusinessException;
import com.ledgerflow.common.error.ErrorCode;
import com.ledgerflow.common.money.CurrencyCode;
import com.ledgerflow.ledger.service.LedgerService;
import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.merchant.repository.MerchantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final ApiKeyService apiKeyService;
    private final LedgerService ledgerService;

    public MerchantService(MerchantRepository merchantRepository,
                           ApiKeyService apiKeyService,
                           LedgerService ledgerService) {
        this.merchantRepository = merchantRepository;
        this.apiKeyService = apiKeyService;
        this.ledgerService = ledgerService;
    }

    public record MerchantRegistrationResult(
            Merchant merchant,
            ApiKeyService.GeneratedApiKey apiKey
    ) {}

    @Transactional
    public MerchantRegistrationResult registerMerchant(String name, String email) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PAYLOAD, "Merchant name cannot be blank");
        }
        if (email == null || email.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PAYLOAD, "Merchant email cannot be blank");
        }
        if (merchantRepository.existsByEmail(email.trim().toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_PAYLOAD, "Merchant with email already exists: " + email);
        }

        Merchant merchant = new Merchant(name.trim(), email.trim().toLowerCase());
        Merchant saved = merchantRepository.save(merchant);

        // Provision initial settlement accounts for supported currencies
        for (CurrencyCode currency : CurrencyCode.values()) {
            ledgerService.getOrCreateMerchantSettlementAccount(saved, currency);
        }

        ApiKeyService.GeneratedApiKey key = apiKeyService.generateApiKey(saved, "Initial Admin Key");
        return new MerchantRegistrationResult(saved, key);
    }

    @Transactional(readOnly = true)
    public Merchant getMerchant(UUID id) {
        return merchantRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Merchant not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Merchant> listMerchants() {
        return merchantRepository.findAll();
    }
}
