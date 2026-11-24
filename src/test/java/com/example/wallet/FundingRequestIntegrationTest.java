package com.example.wallet;

import com.example.wallet.funding.application.FundingRequestService;
import com.example.wallet.funding.application.ScheduleClaimService;
import com.example.wallet.funding.domain.FundingRequestStatus;
import com.example.wallet.payment.application.PaymentService;
import com.example.wallet.shared.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = "wallet.funding-scheduler.enabled=false")
class FundingRequestIntegrationTest {
    private static final UUID REQUEST = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_1 = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_2 = UUID.fromString("30000000-0000-0000-0000-000000000002");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired PaymentService payments;
    @Autowired FundingRequestService requests;
    @Autowired ScheduleClaimService claims;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM idempotency_record");
        jdbc.update("DELETE FROM wallet_entry");
        jdbc.update("DELETE FROM payment");
        jdbc.update("UPDATE wallet SET balance = 10000.00, version = version + 1 WHERE id = '10000000-0000-0000-0000-000000000001'");
        jdbc.update("UPDATE wallet SET balance = 5000.00, version = version + 1 WHERE id = '10000000-0000-0000-0000-000000000002'");
        jdbc.update("UPDATE funding_request SET status = 'APPROVED', completed_items = 0, version = version + 1");
        jdbc.update("UPDATE funding_schedule_item SET claim_token = NULL, claimed_at = NULL");
    }

    @Test
    void schedulerLeasePreventsTwoWorkersFromClaimingSameItem() {
        var firstWorker = claims.claim(LocalDate.now(), 100);
        var secondWorker = claims.claim(LocalDate.now(), 100);

        assertThat(firstWorker.items()).extracting(item -> item.getId()).contains(ITEM_1);
        assertThat(secondWorker.items()).isEmpty();
    }

    @Test
    void onlyOnePaymentCanBeCreatedForScheduleItem() {
        payments.createFundingPayment(REQUEST, ITEM_1);

        assertThatThrownBy(() -> payments.createFundingPayment(REQUEST, ITEM_1))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void suspendedRequestRejectsCreationAndConfirmation() {
        var payment = payments.createFundingPayment(REQUEST, ITEM_1);
        requests.suspend(REQUEST);

        assertThatThrownBy(() -> payments.createFundingPayment(REQUEST, ITEM_2))
            .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> payments.confirm(payment.getId()))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void requestBecomesPartialAndThenFullyPaid() {
        var first = payments.createFundingPayment(REQUEST, ITEM_1);
        var second = payments.createFundingPayment(REQUEST, ITEM_2);

        payments.confirm(first.getId());
        assertThat(requests.get(REQUEST, null, null, 50).request().getStatus()).isEqualTo(FundingRequestStatus.PARTIALLY_PAID);

        payments.confirm(second.getId());
        assertThat(requests.get(REQUEST, null, null, 50).request().getStatus()).isEqualTo(FundingRequestStatus.FULLY_PAID);
    }
}
