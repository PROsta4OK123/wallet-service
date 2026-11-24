package com.example.wallet.wallet.application;

import com.example.wallet.wallet.domain.Wallet;
import com.example.wallet.wallet.infrastructure.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

@Service
public class WalletCommandService {
    private final WalletRepository wallets;
    private final Clock clock;

    public WalletCommandService(WalletRepository wallets, Clock clock) {
        this.wallets = wallets;
        this.clock = clock;
    }

    @Transactional
    public Wallet create(BigDecimal openingBalance) {
        BigDecimal balance = openingBalance == null ? BigDecimal.ZERO : openingBalance;
        return wallets.save(Wallet.create(balance, Instant.now(clock)));
    }
}
