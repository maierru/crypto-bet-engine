package com.cryptobet.engine.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateWalletRequest(
        @NotNull(message = "Initial balance is required")
        @PositiveOrZero(message = "Initial balance must be zero or positive")
        BigDecimal initialBalance
) {}
