package com.ledgerflow.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // 400 Bad Request
    INVALID_PAYLOAD(HttpStatus.BAD_REQUEST, "Invalid request payload or malformed fields"),
    UNSUPPORTED_CURRENCY(HttpStatus.BAD_REQUEST, "Specified currency is not supported"),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "Financial amount must be strictly greater than zero minor units"),

    // 401 Unauthorized
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Missing or invalid API key"),

    // 403 Forbidden
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access forbidden to this resource or tenant boundary exceeded"),

    // 404 Not Found
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource does not exist"),

    // 409 Conflict
    IDEMPOTENCY_KEY_IN_USE(HttpStatus.CONFLICT, "Another concurrent request is currently processing this idempotency key"),
    IDEMPOTENCY_PAYLOAD_MISMATCH(HttpStatus.CONFLICT, "The idempotency key was previously processed with a different payload"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "The resource was modified concurrently, please retry"),

    // 422 Unprocessable Entity
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY, "Wallet balance is insufficient to process this payment"),
    PAYMENT_NOT_IN_REFUNDABLE_STATE(HttpStatus.UNPROCESSABLE_ENTITY, "Payment cannot be refunded in its current state"),
    REFUND_EXCEEDS_CAPACITY(HttpStatus.UNPROCESSABLE_ENTITY, "Refund amount exceeds remaining captured, unrefunded balance"),
    UNBALANCED_JOURNAL_ENTRY(HttpStatus.UNPROCESSABLE_ENTITY, "Financial journal entry is unbalanced; sum(debits) != sum(credits)"),

    // 500 Internal Error
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal server error occurred");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
