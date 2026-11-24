package com.example.wallet.payment.application;

import com.example.wallet.funding.domain.FundingRequest;
import com.example.wallet.funding.domain.FundingScheduleItem;
import com.example.wallet.funding.infrastructure.FundingRequestRepository;
import com.example.wallet.funding.infrastructure.FundingScheduleItemRepository;
import com.example.wallet.payment.domain.Payment;
import com.example.wallet.payment.domain.WalletEntry;
import com.example.wallet.payment.infrastructure.PaymentRepository;
import com.example.wallet.payment.infrastructure.WalletEntryRepository;
import com.example.wallet.shared.error.BusinessRuleException;
import com.example.wallet.shared.error.ResourceNotFoundException;
import com.example.wallet.wallet.domain.Wallet;
import com.example.wallet.wallet.infrastructure.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ConfirmPaymentUseCase {
    private final PaymentRepository payments;
    private final WalletRepository wallets;
    private final WalletEntryRepository entries;
    private final FundingRequestRepository fundingRequests;
    private final FundingScheduleItemRepository scheduleItems;
    private final Clock clock;
    private final PaymentMetrics metrics;

    public ConfirmPaymentUseCase(PaymentRepository payments, WalletRepository wallets,
                                 WalletEntryRepository entries,
                                 FundingRequestRepository fundingRequests,
                                 FundingScheduleItemRepository scheduleItems,
                                 Clock clock, PaymentMetrics metrics) {
        this.payments = payments;
        this.wallets = wallets;
        this.entries = entries;
        this.fundingRequests = fundingRequests;
        this.scheduleItems = scheduleItems;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional
    public Payment confirm(UUID paymentId) {
        long startedAt = System.nanoTime();
        String outcome = "success";
        try {
            return confirmInternal(paymentId);
        } catch (BusinessRuleException rejected) {
            outcome = "rejected";
            throw rejected;
        } catch (RuntimeException failure) {
            outcome = "error";
            throw failure;
        } finally {
            metrics.confirmation(Duration.ofNanos(System.nanoTime() - startedAt), outcome);
        }
    }

    private Payment confirmInternal(UUID paymentId) {
        Payment payment = payments.findByIdForUpdate(paymentId)
            .orElseThrow(() -> notFound("PAYMENT_NOT_FOUND", "Payment", paymentId));
        if (payment.isCompleted()) return payment;

        FundingRequest request = fundingRequestFor(payment);
        Set<UUID> walletIds = new HashSet<>();
        if (payment.getSourceWalletId() != null) walletIds.add(payment.getSourceWalletId());
        if (payment.getTargetWalletId() != null) walletIds.add(payment.getTargetWalletId());
        Map<UUID, Wallet> lockedWallets = wallets.findAllByIdForUpdate(walletIds).stream()
            .collect(Collectors.toMap(Wallet::getId, Function.identity()));
        if (lockedWallets.size() != walletIds.size()) {
            throw new BusinessRuleException("PAYMENT_WALLET_MISSING",
                "A wallet referenced by the payment no longer exists");
        }

        Instant now = clock.instant();
        if (payment.getSourceWalletId() != null) {
            lockedWallets.get(payment.getSourceWalletId()).debit(payment.getAmount(), now);
            entries.save(WalletEntry.debit(payment.getId(), payment.getSourceWalletId(), payment.getAmount(), now));
        }
        if (payment.getTargetWalletId() != null) {
            lockedWallets.get(payment.getTargetWalletId()).credit(payment.getAmount(), now);
            entries.save(WalletEntry.credit(payment.getId(), payment.getTargetWalletId(), payment.getAmount(), now));
        }
        payment.complete(now);
        if (request != null) request.recordCompletedPayment(now);
        return payment;
    }

    private FundingRequest fundingRequestFor(Payment payment) {
        if (payment.getScheduleItemId() == null) return null;
        FundingScheduleItem item = scheduleItems.findById(payment.getScheduleItemId())
            .orElseThrow(() -> notFound("SCHEDULE_ITEM_NOT_FOUND", "Schedule item", payment.getScheduleItemId()));
        FundingRequest request = fundingRequests.findByIdForUpdate(item.getFundingRequestId())
            .orElseThrow(() -> notFound("FUNDING_REQUEST_NOT_FOUND", "Funding request", item.getFundingRequestId()));
        request.ensurePaymentsAllowed();
        return request;
    }

    private ResourceNotFoundException notFound(String code, String kind, UUID id) {
        return new ResourceNotFoundException(code, kind + " " + id + " was not found");
    }
}
