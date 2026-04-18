package com.cryptobet.engine.bet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PlaceBetRequest(
        @NotNull(message = "Wallet ID is required")
        UUID walletId,

        @NotBlank(message = "Symbol is required")
        String symbol,

        @NotBlank(message = "Direction is required")
        String direction,

        @NotNull(message = "Stake is required")
        @Positive(message = "Stake must be positive")
        BigDecimal stake,

        Integer durationSeconds
) {}
