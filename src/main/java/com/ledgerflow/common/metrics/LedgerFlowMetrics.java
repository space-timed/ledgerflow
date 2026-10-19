package com.ledgerflow.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class LedgerFlowMetrics {

    private final MeterRegistry registry;

    private final Counter ledgerInvariantViolationsCounter;

    public LedgerFlowMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.ledgerInvariantViolationsCounter = Counter.builder("ledgerflow_ledger_invariant_violations_total")
                .description("Total number of ledger double-entry or balance invariant violations detected")
                .register(registry);
    }

    public void recordPaymentSuccess(String currency, long durationNanos) {
        Counter.builder("ledgerflow_payments_total")
                .description("Total number of payment attempts")
                .tag("status", "CAPTURED")
                .tag("currency", currency)
                .register(registry)
                .increment();

        Timer.builder("ledgerflow_payment_duration_seconds")
                .description("Latency distribution for payment processing")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordPaymentFailure(String currency, String reason, long durationNanos) {
        Counter.builder("ledgerflow_payments_total")
                .description("Total number of payment attempts")
                .tag("status", "FAILED")
                .tag("currency", currency)
                .tag("reason", reason)
                .register(registry)
                .increment();

        Timer.builder("ledgerflow_payment_duration_seconds")
                .description("Latency distribution for payment processing")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordRefundSuccess(String currency, long durationNanos) {
        Counter.builder("ledgerflow_refunds_total")
                .description("Total number of refunds processed")
                .tag("status", "SUCCESS")
                .tag("currency", currency)
                .register(registry)
                .increment();

        Timer.builder("ledgerflow_refund_duration_seconds")
                .description("Latency distribution for refund processing")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordInvariantViolation() {
        ledgerInvariantViolationsCounter.increment();
    }
}
