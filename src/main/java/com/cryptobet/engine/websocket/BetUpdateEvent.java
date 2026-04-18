package com.cryptobet.engine.websocket;

import com.cryptobet.engine.bet.BetStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record BetUpdateEvent(UUID betId, UUID walletId, BetStatus status, BigDecimal payout) {
}
