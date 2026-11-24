package com.example.wallet;

import com.example.wallet.payment.application.PaymentService;
import com.example.wallet.payment.domain.Payment;
import com.example.wallet.payment.domain.PaymentStatus;
import com.example.wallet.shared.error.BusinessRuleException;
import com.example.wallet.wallet.infrastructure.WalletRepository;
import com.example.wallet.wallet.application.LedgerReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = "wallet.funding-scheduler.enabled=false")
class PaymentIntegrationTest {
    private static final UUID SOURCE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired PaymentService payments;
    @Autowired WalletRepository wallets;
    @Autowired JdbcTemplate jdbc;
    @Autowired LedgerReconciliationService reconciliation;

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM idempotency_record");
        jdbc.update("DELETE FROM wallet_entry");
        jdbc.update("DELETE FROM payment");
        jdbc.update("UPDATE wallet SET balance = 10000.00, version = version + 1 WHERE id = ?", SOURCE);
        jdbc.update("UPDATE wallet SET balance = 5000.00, version = version + 1 WHERE id = ?", TARGET);
        jdbc.update("UPDATE funding_request SET status = 'APPROVED', completed_items = 0, version = version + 1");
    }

    @Test
    void sameIdempotencyKeyReturnsSamePayment() {
        Payment first = payments.createTransfer(SOURCE, TARGET, new BigDecimal("125.00"), "request-42");
        Payment retry = payments.createTransfer(SOURCE, TARGET, new BigDecimal("125.00"), "request-42");

        assertThat(retry.getId()).isEqualTo(first.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment", Long.class)).isEqualTo(1L);
    }

    @Test
    void idempotencyKeyCannotBeReusedForDifferentPayload() {
        payments.createTransfer(SOURCE, TARGET, new BigDecimal("125.00"), "request-42");

        assertThatThrownBy(() ->
            payments.createTransfer(SOURCE, TARGET, new BigDecimal("126.00"), "request-42"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("different request");
    }

    @Test
    void creatingPaymentDoesNotMoveMoney() {
        Payment payment = payments.createTransfer(SOURCE, TARGET, new BigDecimal("125.00"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(balance(SOURCE)).isEqualByComparingTo("10000.00");
        assertThat(balance(TARGET)).isEqualByComparingTo("5000.00");
    }

    @Test
    void confirmationMovesMoneyExactlyOnce() {
        Payment payment = payments.createTransfer(SOURCE, TARGET, new BigDecimal("125.00"));

        payments.confirm(payment.getId());
        payments.confirm(payment.getId());

        assertThat(balance(SOURCE)).isEqualByComparingTo("9875.00");
        assertThat(balance(TARGET)).isEqualByComparingTo("5125.00");
        assertThat(payments.get(payment.getId()).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wallet_entry WHERE payment_id = ?",
            Long.class, payment.getId())).isEqualTo(2L);
        assertThat(reconciliation.verifyAll().consistent()).isTrue();
    }

    @Test
    void insufficientFundsRollsBackConfirmation() {
        Payment payment = payments.createWithdrawal(SOURCE, new BigDecimal("10000.01"));

        assertThatThrownBy(() -> payments.confirm(payment.getId()))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("insufficient");
        assertThat(balance(SOURCE)).isEqualByComparingTo("10000.00");
        assertThat(payments.get(payment.getId()).getStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void concurrentWithdrawalsCannotOverdrawWallet() throws Exception {
        Payment first = payments.createWithdrawal(SOURCE, new BigDecimal("7000.00"));
        Payment second = payments.createWithdrawal(SOURCE, new BigDecimal("7000.00"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> firstResult = executor.submit(() -> confirmAfter(start, first.getId()));
            Future<Boolean> secondResult = executor.submit(() -> confirmAfter(start, second.getId()));
            start.countDown();

            assertThat(firstResult.get(10, TimeUnit.SECONDS) ^ secondResult.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(balance(SOURCE)).isEqualByComparingTo("3000.00");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentConfirmationExecutesPaymentExactlyOnce() throws Exception {
        Payment payment = payments.createTransfer(SOURCE, TARGET, new BigDecimal("10.00"));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Payment>> results = IntStream.range(0, 20)
                .mapToObj(index -> executor.submit(() -> payments.confirm(payment.getId())))
                .toList();
            for (Future<Payment> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            }
        }

        assertThat(balance(SOURCE)).isEqualByComparingTo("9990.00");
        assertThat(balance(TARGET)).isEqualByComparingTo("5010.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wallet_entry WHERE payment_id = ?",
            Long.class, payment.getId())).isEqualTo(2L);
        assertThat(reconciliation.verifyAll().consistent()).isTrue();
    }

    @Test
    void concurrentIdempotentCreationReturnsSinglePayment() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<UUID>> results = IntStream.range(0, 20)
                .mapToObj(index -> executor.submit(() -> payments.createTransfer(
                    SOURCE, TARGET, new BigDecimal("1.00"), "concurrent-request").getId()))
                .toList();

            UUID expected = results.getFirst().get(10, TimeUnit.SECONDS);
            for (Future<UUID> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(expected);
            }
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_record", Long.class)).isEqualTo(1L);
    }

    private boolean confirmAfter(CountDownLatch start, UUID paymentId) throws InterruptedException {
        start.await();
        try {
            payments.confirm(paymentId);
            return true;
        } catch (BusinessRuleException expected) {
            return false;
        }
    }

    private BigDecimal balance(UUID id) {
        return wallets.findById(id).orElseThrow().getBalance();
    }
}
