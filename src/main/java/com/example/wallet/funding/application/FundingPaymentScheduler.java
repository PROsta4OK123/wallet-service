package com.example.wallet.funding.application;

import com.example.wallet.payment.application.PaymentService;
import com.example.wallet.shared.error.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;

import java.time.Clock;
import java.time.LocalDate;

@Component
@ConditionalOnProperty(name = "wallet.funding-scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class FundingPaymentScheduler {
    private static final Logger log = LoggerFactory.getLogger(FundingPaymentScheduler.class);
    private final ScheduleClaimService claims;
    private final PaymentService payments;
    private final Clock clock;
    private final int batchSize;

    public FundingPaymentScheduler(ScheduleClaimService claims,
                                   PaymentService payments, Clock clock,
                                   @Value("${wallet.funding-scheduler.batch-size:100}") int batchSize) {
        this.claims = claims;
        this.payments = payments;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${wallet.funding-scheduler.fixed-delay}")
    public void createDuePayments() {
        var batch = claims.claim(LocalDate.now(clock), batchSize);
        for (var item : batch.items()) {
            try {
                payments.createDueFundingPayment(item.getId());
            } catch (BusinessRuleException raceOrStateChange) {
                log.debug("Skipped schedule item {}: {}", item.getId(), raceOrStateChange.getMessage());
                claims.release(item.getId(), batch.token());
            } catch (RuntimeException failure) {
                log.error("Could not create payment for schedule item {}", item.getId(), failure);
                claims.release(item.getId(), batch.token());
            }
        }
    }
}
