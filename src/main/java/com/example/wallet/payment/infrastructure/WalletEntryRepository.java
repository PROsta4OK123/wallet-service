package com.example.wallet.payment.infrastructure;

import com.example.wallet.payment.domain.WalletEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WalletEntryRepository extends JpaRepository<WalletEntry, UUID> {}
