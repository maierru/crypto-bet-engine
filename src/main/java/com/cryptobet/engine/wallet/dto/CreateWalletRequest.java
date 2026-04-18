package com.cryptobet.engine.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateWalletRequest(
        @NotNull(message = "Initial balance is required")
        @PositiveOrZero(message = "Initial balance must be zero or positive")
        BigDecimal initialBalance,

        @Size(max = 50, message = "Nickname must be at most 50 characters")
        String nickname
) {}
