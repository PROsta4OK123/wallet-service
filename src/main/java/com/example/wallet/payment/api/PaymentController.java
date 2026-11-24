package com.example.wallet.payment.api;

import com.example.wallet.payment.application.PaymentService;
import com.example.wallet.payment.domain.Payment;
import com.example.wallet.payment.domain.PaymentStatus;
import com.example.wallet.payment.domain.PaymentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Two-stage deposit, withdrawal and transfer operations")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) { this.payments = payments; }

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a wallet deposit", description = "Creates a payment without changing the balance")
    PaymentResponse createDeposit(@RequestHeader(name = "Idempotency-Key", required = false) String key,
                                  @Valid @RequestBody WalletPaymentRequest request) {
        return PaymentResponse.from(payments.createDeposit(request.walletId(), request.amount(), key));
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a wallet withdrawal", description = "Funds are checked only during confirmation")
    PaymentResponse createWithdrawal(@RequestHeader(name = "Idempotency-Key", required = false) String key,
                                     @Valid @RequestBody WalletPaymentRequest request) {
        return PaymentResponse.from(payments.createWithdrawal(request.walletId(), request.amount(), key));
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a wallet transfer", description = "Supports Idempotency-Key for safe client retries")
    PaymentResponse createTransfer(@RequestHeader(name = "Idempotency-Key", required = false) String key,
                                   @Valid @RequestBody TransferRequest request) {
        return PaymentResponse.from(payments.createTransfer(
            request.sourceWalletId(), request.targetWalletId(), request.amount(), key));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm and execute a payment", description = "Idempotently moves money in one database transaction")
    PaymentResponse confirm(@PathVariable UUID id) {
        return PaymentResponse.from(payments.confirm(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a payment")
    PaymentResponse get(@PathVariable UUID id) {
        return PaymentResponse.from(payments.get(id));
    }

    public record WalletPaymentRequest(
        @NotNull UUID walletId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount) {}

    public record TransferRequest(
        @NotNull UUID sourceWalletId,
        @NotNull UUID targetWalletId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount) {}

    public record PaymentResponse(UUID id, PaymentType type, PaymentStatus status, BigDecimal amount,
                                  UUID sourceWalletId, UUID targetWalletId, UUID scheduleItemId,
                                  Instant createdAt, Instant completedAt) {
        public static PaymentResponse from(Payment payment) {
            return new PaymentResponse(payment.getId(), payment.getType(), payment.getStatus(),
                payment.getAmount(), payment.getSourceWalletId(), payment.getTargetWalletId(),
                payment.getScheduleItemId(), payment.getCreatedAt(), payment.getCompletedAt());
        }
    }
}
