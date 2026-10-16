package com.ledgerflow.merchant.service;

import com.ledgerflow.common.error.BusinessException;
import com.ledgerflow.common.error.ErrorCode;
import com.ledgerflow.merchant.domain.ApiKey;
import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.merchant.domain.MerchantStatus;
import com.ledgerflow.merchant.repository.ApiKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class ApiKeyService {

    private static final String KEY_PREFIX = "lf_live_";
    private static final SecureRandom secureRandom = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    public record GeneratedApiKey(
            UUID id,
            String rawApiKey,
            String keyPrefix,
            String name,
            Instant createdAt
    ) {}

    @Transactional
    public GeneratedApiKey generateApiKey(Merchant merchant, String keyName) {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        String secretPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String rawApiKey = KEY_PREFIX + secretPart;
        String prefix = rawApiKey.substring(0, Math.min(14, rawApiKey.length()));

        String hash = hashKey(rawApiKey);

        ApiKey apiKey = new ApiKey(merchant, prefix, hash, (keyName != null && !keyName.isBlank()) ? keyName : "Default Key");
        ApiKey saved = apiKeyRepository.save(apiKey);

        return new GeneratedApiKey(saved.getId(), rawApiKey, prefix, saved.getName(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public Merchant authenticate(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing X-Api-Key header");
        }

        String hash = hashKey(rawApiKey.trim());
        ApiKey apiKey = apiKeyRepository.findByKeyHashAndStatus(hash, MerchantStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid or inactive API key"));

        if (apiKey.getMerchant().getStatus() != MerchantStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Merchant account is " + apiKey.getMerchant().getStatus());
        }

        return apiKey.getMerchant();
    }

    public static String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
