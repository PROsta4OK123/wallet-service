package com.example.wallet.wallet.application;

import com.example.wallet.shared.error.ResourceNotFoundException;
import com.example.wallet.wallet.domain.Wallet;
import com.example.wallet.wallet.infrastructure.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WalletQueryService {
    private final WalletRepository wallets;

    public WalletQueryService(WalletRepository wallets) { this.wallets = wallets; }

    @Transactional(readOnly = true)
    public Wallet get(UUID id) {
        return wallets.findById(id).orElseThrow(() ->
            new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet " + id + " was not found"));
    }
}
