package com.ledgerflow.payment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.common.money.CurrencyCode;
import com.ledgerflow.common.security.MerchantContext;
import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.payment.service.IdempotencyService;
import com.ledgerflow.payment.service.PaymentService;
import com.ledgerflow.refund.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments & Refunds", description = "Idempotent payment creation, capture, inquiry, and refund operations")
public class PaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public PaymentController(PaymentService paymentService,
                             RefundService refundService,
                             IdempotencyService idempotencyService,
                             ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    public record CreatePaymentRequest(
            @NotNull(message = "Customer wallet ID is required") UUID customerWalletId,
            @Min(value = 1, message = "Amount must be at least 1 minor unit") long amountMinorUnits,
            @NotBlank(message = "Currency code is required") String currency,
            String description
    ) {}

    public record CreateRefundRequest(
            @Min(value = 1, message = "Refund amount must be at least 1 minor unit") long amountMinorUnits,
            String reason
    ) {}

    @PostMapping
    @Operation(summary = "Process and capture an idempotent payment", description = "Atomically verifies customer wallet funds, acquires row locks, posts double-entry journal, and records outbox event.")
    public ResponseEntity<String> createPayment(
            @Parameter(description = "Mandatory unique idempotency key for this request", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) throws JsonProcessingException {

        Merchant merchant = MerchantContext.getMerchant();
        String requestJson = objectMapper.writeValueAsString(request);
        String fingerprint = IdempotencyService.computeRequestFingerprint("POST", "/api/v1/payments", requestJson);

        Optional<IdempotencyService.CachedResponse> cachedOpt = idempotencyService
                .checkOrReserveIdempotencyKey(merchant, idempotencyKey, fingerprint);

        if (cachedOpt.isPresent()) {
            return ResponseEntity.status(cachedOpt.get().statusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cachedOpt.get().body());
        }

        try {
            CurrencyCode currency = CurrencyCode.fromString(request.currency());
            PaymentService.PaymentResult result = paymentService.processPayment(
                    merchant,
                    request.customerWalletId(),
                    request.amountMinorUnits(),
                    currency,
                    request.description(),
                    idempotencyKey
            );

            String responseJson = objectMapper.writeValueAsString(result);
            idempotencyService.recordCompletion(merchant, idempotencyKey, HttpStatus.CREATED.value(), responseJson);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(responseJson);
        } catch (Exception e) {
            idempotencyService.recordFailure(merchant, idempotencyKey);
            throw e;
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment details by ID")
    public ResponseEntity<PaymentService.PaymentResult> getPayment(@PathVariable UUID id) {
        Merchant merchant = MerchantContext.getMerchant();
        PaymentService.PaymentResult result = paymentService.getPayment(id, merchant.getId());
        return ResponseEntity.ok(result);
    }

    @GetMapping
    @Operation(summary = "List merchant payments", description = "Retrieves recent payments processed by the authenticated merchant in reverse chronological order.")
    public ResponseEntity<List<PaymentService.PaymentResult>> listPayments() {
        Merchant merchant = MerchantContext.getMerchant();
        List<PaymentService.PaymentResult> results = paymentService.listPayments(merchant.getId());
        return ResponseEntity.ok(results);
    }

    @PostMapping("/{id}/refunds")
    @Operation(summary = "Process a full or partial refund", description = "Validates refund capacity against original capture, posts reversing journal entries, and updates payment state.")
    public ResponseEntity<String> createRefund(
            @PathVariable UUID id,
            @Parameter(description = "Mandatory unique idempotency key for this refund", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest request) throws JsonProcessingException {

        Merchant merchant = MerchantContext.getMerchant();
        String requestJson = objectMapper.writeValueAsString(request);
        String fingerprint = IdempotencyService.computeRequestFingerprint("POST", "/api/v1/payments/" + id + "/refunds", requestJson);

        Optional<IdempotencyService.CachedResponse> cachedOpt = idempotencyService
                .checkOrReserveIdempotencyKey(merchant, idempotencyKey, fingerprint);

        if (cachedOpt.isPresent()) {
            return ResponseEntity.status(cachedOpt.get().statusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cachedOpt.get().body());
        }

        try {
            RefundService.RefundResult result = refundService.processRefund(
                    merchant,
                    id,
                    request.amountMinorUnits(),
                    request.reason(),
                    idempotencyKey
            );

            String responseJson = objectMapper.writeValueAsString(result);
            idempotencyService.recordCompletion(merchant, idempotencyKey, HttpStatus.CREATED.value(), responseJson);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(responseJson);
        } catch (Exception e) {
            idempotencyService.recordFailure(merchant, idempotencyKey);
            throw e;
        }
    }

    @GetMapping("/{id}/refunds")
    @Operation(summary = "List all refunds issued for a payment")
    public ResponseEntity<List<RefundService.RefundResult>> listRefunds(@PathVariable UUID id) {
        Merchant merchant = MerchantContext.getMerchant();
        List<RefundService.RefundResult> results = refundService.listRefundsForPayment(id, merchant.getId());
        return ResponseEntity.ok(results);
    }
}
