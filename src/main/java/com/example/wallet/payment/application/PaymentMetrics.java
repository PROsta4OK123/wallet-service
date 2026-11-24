package com.example.wallet.payment.application;

import com.example.wallet.payment.domain.PaymentType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

@Component
public class PaymentMetrics {
    private final MeterRegistry registry;

    public PaymentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void created(PaymentType type) {
        Runnable increment = () -> Counter.builder("wallet.payments.created")
            .tag("type", type.name().toLowerCase())
            .register(registry).increment();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { increment.run(); }
            });
        } else {
            increment.run();
        }
    }

    public void confirmation(Duration duration, String outcome) {
        Timer.builder("wallet.payment.confirmation")
            .tag("outcome", outcome)
            .publishPercentileHistogram()
            .register(registry)
            .record(duration);
    }

    public void idempotencyHit() {
        registry.counter("wallet.idempotency.hits").increment();
    }
}
