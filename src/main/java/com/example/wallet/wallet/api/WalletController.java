package com.example.wallet.wallet.api;

import com.example.wallet.wallet.application.WalletCommandService;
import com.example.wallet.wallet.application.WalletQueryService;
import com.example.wallet.wallet.domain.Wallet;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@Tag(name = "Wallets", description = "Wallet balances")
public class WalletController {
    private final WalletQueryService wallets;
    private final WalletCommandService commands;

    public WalletController(WalletQueryService wallets, WalletCommandService commands) {
        this.wallets = wallets;
        this.commands = commands;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a wallet",
        description = "Creates a wallet with an optional non-negative opening balance")
    WalletResponse create(@Valid @RequestBody CreateWalletRequest request) {
        return WalletResponse.from(commands.create(request.openingBalance()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a wallet and its current balance")
    WalletResponse get(@PathVariable UUID id) {
        return WalletResponse.from(wallets.get(id));
    }

    public record WalletResponse(UUID id, BigDecimal balance) {
        static WalletResponse from(Wallet wallet) {
            return new WalletResponse(wallet.getId(), wallet.getBalance());
        }
    }

    public record CreateWalletRequest(
        @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2) BigDecimal openingBalance) {}
}
