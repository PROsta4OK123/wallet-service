package com.example.wallet.payment.application;

import com.example.wallet.funding.domain.FundingRequest;
import com.example.wallet.funding.domain.FundingScheduleItem;
import com.example.wallet.funding.infrastructure.FundingRequestRepository;
import com.example.wallet.funding.infrastructure.FundingScheduleItemRepository;
import com.example.wallet.payment.domain.Payment;
import com.example.wallet.payment.infrastructure.IdempotencyStore;
import com.example.wallet.payment.infrastructure.PaymentRepository;
import com.example.wallet.shared.error.BusinessRuleException;
import com.example.wallet.shared.error.ResourceNotFoundException;
import com.example.wallet.wallet.infrastructure.WalletRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class CreatePaymentUseCase {
    private final PaymentRepository payments;
    private final WalletRepository wallets;
    private final IdempotencyStore idempotency;
    private final FundingRequestRepository fundingRequests;
    private final FundingScheduleItemRepository scheduleItems;
    private final Clock clock;
    private final PaymentMetrics metrics;

    public CreatePaymentUseCase(PaymentRepository payments, WalletRepository wallets,
                                IdempotencyStore idempotency,
                                FundingRequestRepository fundingRequests,
                                FundingScheduleItemRepository scheduleItems,
                                Clock clock, PaymentMetrics metrics) {
        this.payments = payments;
        this.wallets = wallets;
        this.idempotency = idempotency;
        this.fundingRequests = fundingRequests;
        this.scheduleItems = scheduleItems;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional
    public Payment createDeposit(UUID walletId, BigDecimal amount, String key) {
        BigDecimal normalized = normalize(amount);
        return idempotent(key, "DEPOSIT|" + walletId + "|" + normalized, () -> {
            requireWallet(walletId);
            return saved(Payment.deposit(walletId, normalized, clock.instant()));
        });
    }

    @Transactional
    public Payment createWithdrawal(UUID walletId, BigDecimal amount, String key) {
        BigDecimal normalized = normalize(amount);
        return idempotent(key, "WITHDRAWAL|" + walletId + "|" + normalized, () -> {
            requireWallet(walletId);
            return saved(Payment.withdrawal(walletId, normalized, clock.instant()));
        });
    }

    @Transactional
    public Payment createTransfer(UUID sourceId, UUID targetId, BigDecimal amount, String key) {
        BigDecimal normalized = normalize(amount);
        return idempotent(key, "TRANSFER|" + sourceId + "|" + targetId + "|" + normalized, () -> {
            requireDistinctExistingWallets(sourceId, targetId);
            return saved(Payment.transfer(sourceId, targetId, normalized, null, clock.instant()));
        });
    }

    @Transactional
    public Payment createFundingPayment(UUID requestId, UUID itemId) {
        FundingRequest request = fundingRequests.findByIdForUpdate(requestId)
            .orElseThrow(() -> notFound("FUNDING_REQUEST_NOT_FOUND", "Funding request", requestId));
        request.ensurePaymentsAllowed();
        FundingScheduleItem item = scheduleItems.findById(itemId)
            .filter(value -> value.getFundingRequestId().equals(requestId))
            .orElseThrow(() -> notFound("SCHEDULE_ITEM_NOT_FOUND", "Schedule item", itemId));
        return createScheduleItemPayment(request, item);
    }

    @Transactional
    public Payment createDueFundingPayment(UUID itemId) {
        FundingScheduleItem item = scheduleItems.findById(itemId)
            .orElseThrow(() -> notFound("SCHEDULE_ITEM_NOT_FOUND", "Schedule item", itemId));
        FundingRequest request = fundingRequests.findByIdForUpdate(item.getFundingRequestId())
            .orElseThrow(() -> notFound("FUNDING_REQUEST_NOT_FOUND", "Funding request", item.getFundingRequestId()));
        request.ensurePaymentsAllowed();
        return createScheduleItemPayment(request, item);
    }

    private Payment createScheduleItemPayment(FundingRequest request, FundingScheduleItem item) {
        if (payments.existsByScheduleItemId(item.getId())) {
            throw duplicateSchedulePayment();
        }
        try {
            Payment payment = payments.saveAndFlush(Payment.transfer(request.getSourceWalletId(),
                request.getTargetWalletId(), item.getAmount(), item.getId(), clock.instant()));
            metrics.created(payment.getType());
            return payment;
        } catch (DataIntegrityViolationException duplicate) {
            throw duplicateSchedulePayment();
        }
    }

    private Payment saved(Payment payment) {
        Payment result = payments.save(payment);
        metrics.created(result.getType());
        return result;
    }

    private Payment idempotent(String key, String fingerprint, Supplier<Payment> creator) {
        if (key == null || key.isBlank()) return creator.get();
        if (key.length() > 128) {
            throw new BusinessRuleException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must not exceed 128 characters");
        }
        if (idempotency.reserve(key, fingerprint, clock.instant())) {
            Payment created = creator.get();
            payments.flush();
            idempotency.attachPayment(key, created.getId());
            return created;
        }
        IdempotencyStore.ExistingRequest existing = idempotency.get(key);
        if (!existing.fingerprint().equals(fingerprint)) {
            throw new BusinessRuleException("IDEMPOTENCY_KEY_REUSED",
                "Idempotency-Key was already used for a different request");
        }
        if (existing.paymentId() == null) {
            throw new BusinessRuleException("IDEMPOTENCY_REQUEST_IN_PROGRESS",
                "The request with this Idempotency-Key is still in progress");
        }
        metrics.idempotencyHit();
        return payments.findById(existing.paymentId())
            .orElseThrow(() -> new BusinessRuleException("IDEMPOTENCY_STATE_ERROR",
                "Idempotency record references a missing payment"));
    }

    private void requireWallet(UUID id) {
        if (!wallets.existsById(id)) throw notFound("WALLET_NOT_FOUND", "Wallet", id);
    }

    private void requireDistinctExistingWallets(UUID sourceId, UUID targetId) {
        if (sourceId.equals(targetId)) {
            throw new BusinessRuleException("SAME_WALLET_TRANSFER", "Source and target wallets must differ");
        }
        requireWallet(sourceId);
        requireWallet(targetId);
    }

    private BigDecimal normalize(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new BusinessRuleException("INVALID_AMOUNT", "Amount must be greater than zero");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException error) {
            throw new BusinessRuleException("INVALID_AMOUNT_SCALE", "Amount must have at most two decimal places");
        }
    }

    private BusinessRuleException duplicateSchedulePayment() {
        return new BusinessRuleException("SCHEDULE_ITEM_PAYMENT_EXISTS",
            "A payment already exists for this schedule item");
    }

    private ResourceNotFoundException notFound(String code, String kind, UUID id) {
        return new ResourceNotFoundException(code, kind + " " + id + " was not found");
    }
}
