package com.example.wallet.payment.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet_entry")
public class WalletEntry {
    @Id private UUID id;
    private UUID paymentId;
    private UUID walletId;
    @Enumerated(EnumType.STRING) private EntryDirection direction;
    private BigDecimal amount;
    private Instant createdAt;

    protected WalletEntry() {}

    private WalletEntry(UUID paymentId, UUID walletId, EntryDirection direction,
                        BigDecimal amount, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.walletId = walletId;
        this.direction = direction;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public static WalletEntry debit(UUID paymentId, UUID walletId, BigDecimal amount, Instant now) {
        return new WalletEntry(paymentId, walletId, EntryDirection.DEBIT, amount, now);
    }

    public static WalletEntry credit(UUID paymentId, UUID walletId, BigDecimal amount, Instant now) {
        return new WalletEntry(paymentId, walletId, EntryDirection.CREDIT, amount, now);
    }
}
