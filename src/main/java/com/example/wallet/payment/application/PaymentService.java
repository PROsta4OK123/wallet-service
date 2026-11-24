package com.example.wallet.payment.application;

import com.example.wallet.payment.domain.Payment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stable application facade. Transaction boundaries live in focused use cases.
 */
@Service
public class PaymentService {
    private final CreatePaymentUseCase creation;
    private final ConfirmPaymentUseCase confirmation;
    private final PaymentQueryService queries;

    public PaymentService(CreatePaymentUseCase creation, ConfirmPaymentUseCase confirmation,
                          PaymentQueryService queries) {
        this.creation = creation;
        this.confirmation = confirmation;
        this.queries = queries;
    }

    public Payment createDeposit(UUID walletId, BigDecimal amount) {
        return creation.createDeposit(walletId, amount, null);
    }

    public Payment createDeposit(UUID walletId, BigDecimal amount, String idempotencyKey) {
        return creation.createDeposit(walletId, amount, idempotencyKey);
    }

    public Payment createWithdrawal(UUID walletId, BigDecimal amount) {
        return creation.createWithdrawal(walletId, amount, null);
    }

    public Payment createWithdrawal(UUID walletId, BigDecimal amount, String idempotencyKey) {
        return creation.createWithdrawal(walletId, amount, idempotencyKey);
    }

    public Payment createTransfer(UUID sourceId, UUID targetId, BigDecimal amount) {
        return creation.createTransfer(sourceId, targetId, amount, null);
    }

    public Payment createTransfer(UUID sourceId, UUID targetId, BigDecimal amount, String idempotencyKey) {
        return creation.createTransfer(sourceId, targetId, amount, idempotencyKey);
    }

    public Payment createFundingPayment(UUID requestId, UUID itemId) {
        return creation.createFundingPayment(requestId, itemId);
    }

    public Payment createDueFundingPayment(UUID itemId) {
        return creation.createDueFundingPayment(itemId);
    }

    public Payment confirm(UUID paymentId) {
        return confirmation.confirm(paymentId);
    }

    public Payment get(UUID id) {
        return queries.get(id);
    }
}
