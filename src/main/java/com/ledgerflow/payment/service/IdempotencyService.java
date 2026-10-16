package com.ledgerflow.payment.service;

import com.ledgerflow.common.error.BusinessException;
import com.ledgerflow.common.error.ErrorCode;
import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.payment.domain.IdempotencyRecord;
import com.ledgerflow.payment.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(60);

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public IdempotencyService(IdempotencyRecordRepository idempotencyRecordRepository) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    public record CachedResponse(int statusCode, String body) {}

    public static String computeRequestFingerprint(String method, String path, String payload) {
        String canonicalString = (method != null ? method.toUpperCase() : "GET") + ":"
                + (path != null ? path : "/") + ":"
                + (payload != null ? payload.trim() : "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalString.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    @Transactional
    public Optional<CachedResponse> checkOrReserveIdempotencyKey(Merchant merchant,
                                                                 String idempotencyKey,
                                                                 String requestFingerprint) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PAYLOAD, "Idempotency-Key header is mandatory for financial mutations");
        }

        Optional<IdempotencyRecord> existingOpt = idempotencyRecordRepository
                .findByMerchantIdAndIdempotencyKey(merchant.getId(), idempotencyKey.trim());

        if (existingOpt.isPresent()) {
            IdempotencyRecord record = existingOpt.get();

            // 1. Verify payload fingerprint
            if (!record.getRequestHash().equals(requestFingerprint)) {
                log.warn("Idempotency key [{}] reused with mismatching fingerprint by merchant [{}]",
                        idempotencyKey, merchant.getId());
                throw new BusinessException(ErrorCode.IDEMPOTENCY_PAYLOAD_MISMATCH,
                        "Idempotency-Key '" + idempotencyKey + "' was already used with a different request payload.");
            }

            // 2. Check if currently in-progress
            if ("IN_PROGRESS".equals(record.getStatus())) {
                if (record.getLockedUntil().isAfter(Instant.now())) {
                    log.warn("Concurrent request with in-progress idempotency key [{}]", idempotencyKey);
                    throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_IN_USE,
                            "A concurrent request is currently in progress for Idempotency-Key '" + idempotencyKey + "'.");
                }
                // Lock expired, update lock
                record.setLockedUntil(Instant.now().plus(LOCK_TIMEOUT));
                idempotencyRecordRepository.save(record);
                return Optional.empty();
            }

            // 3. Completed: return cached response
            if ("COMPLETED".equals(record.getStatus())) {
                log.info("Idempotent replay for key [{}] by merchant [{}]", idempotencyKey, merchant.getId());
                return Optional.of(new CachedResponse(record.getResponseCode(), record.getResponseBody()));
            }
        }

        // Reserve new record
        IdempotencyRecord newRecord = new IdempotencyRecord(
                merchant,
                idempotencyKey.trim(),
                requestFingerprint,
                Instant.now().plus(LOCK_TIMEOUT)
        );
        idempotencyRecordRepository.save(newRecord);
        return Optional.empty();
    }

    @Transactional
    public void recordCompletion(Merchant merchant, String idempotencyKey, int statusCode, String responseJson) {
        idempotencyRecordRepository.findByMerchantIdAndIdempotencyKey(merchant.getId(), idempotencyKey.trim())
                .ifPresent(record -> {
                    record.setStatus("COMPLETED");
                    record.setResponseCode(statusCode);
                    record.setResponseBody(responseJson);
                    idempotencyRecordRepository.save(record);
                });
    }

    @Transactional
    public void recordFailure(Merchant merchant, String idempotencyKey) {
        idempotencyRecordRepository.findByMerchantIdAndIdempotencyKey(merchant.getId(), idempotencyKey.trim())
                .ifPresent(idempotencyRecordRepository::delete);
    }
}
