package com.cryptobet.engine.bet.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PlaceBetRequest(
        UUID walletId,
        String symbol,
        String direction,
        BigDecimal stake,
        BigDecimal entryPrice
) {}
