package com.example.wallet.wallet.domain;

import com.example.wallet.shared.error.BusinessRuleException;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet")
public class Wallet {
    @Id private UUID id;
    private BigDecimal balance;
    private BigDecimal openingBalance;
    private Instant createdAt;
    private Instant updatedAt;
    @Version private long version;

    protected Wallet() {}

    public static Wallet create(BigDecimal openingBalance, Instant now) {
        Wallet wallet = new Wallet();
        wallet.id = UUID.randomUUID();
        wallet.balance = openingBalance;
        wallet.openingBalance = openingBalance;
        wallet.createdAt = now;
        wallet.updatedAt = now;
        return wallet;
    }

    public UUID getId() { return id; }
    public BigDecimal getBalance() { return balance; }
    public BigDecimal getOpeningBalance() { return openingBalance; }

    public void credit(BigDecimal amount, Instant now) {
        balance = balance.add(amount);
        updatedAt = now;
    }

    public void debit(BigDecimal amount, Instant now) {
        if (balance.compareTo(amount) < 0) {
            throw new BusinessRuleException("INSUFFICIENT_FUNDS", "Wallet has insufficient funds");
        }
        balance = balance.subtract(amount);
        updatedAt = now;
    }
}
