package com.example.wallet.payment.domain;

import com.example.wallet.shared.error.BusinessRuleException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment")
public class Payment {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) private PaymentType type;
    @Enumerated(EnumType.STRING) private PaymentStatus status;
    private BigDecimal amount;
    private UUID sourceWalletId;
    private UUID targetWalletId;
    private UUID scheduleItemId;
    private Instant createdAt;
    private Instant completedAt;
    @Version private long version;

    protected Payment() {}

    private Payment(PaymentType type, BigDecimal amount, UUID sourceWalletId,
                    UUID targetWalletId, UUID scheduleItemId, Instant now) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.status = PaymentStatus.CREATED;
        this.amount = amount;
        this.sourceWalletId = sourceWalletId;
        this.targetWalletId = targetWalletId;
        this.scheduleItemId = scheduleItemId;
        this.createdAt = now;
    }

    public static Payment deposit(UUID walletId, BigDecimal amount, Instant now) {
        return new Payment(PaymentType.DEPOSIT, amount, null, walletId, null, now);
    }

    public static Payment withdrawal(UUID walletId, BigDecimal amount, Instant now) {
        return new Payment(PaymentType.WITHDRAWAL, amount, walletId, null, null, now);
    }

    public static Payment transfer(UUID sourceId, UUID targetId, BigDecimal amount,
                                   UUID scheduleItemId, Instant now) {
        if (sourceId.equals(targetId)) {
            throw new BusinessRuleException("SAME_WALLET_TRANSFER", "Source and target wallets must differ");
        }
        return new Payment(PaymentType.TRANSFER, amount, sourceId, targetId, scheduleItemId, now);
    }

    public boolean isCompleted() { return status == PaymentStatus.COMPLETED; }
    public void complete(Instant now) { status = PaymentStatus.COMPLETED; completedAt = now; }
    public UUID getId() { return id; }
    public PaymentType getType() { return type; }
    public PaymentStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public UUID getSourceWalletId() { return sourceWalletId; }
    public UUID getTargetWalletId() { return targetWalletId; }
    public UUID getScheduleItemId() { return scheduleItemId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
