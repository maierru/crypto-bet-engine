package com.cryptobet.engine.bet.dto;

import com.cryptobet.engine.bet.Bet;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.UUID;

public record BetResponse(
        UUID id,
        UUID walletId,
        String symbol,
        String direction,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal stake,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal entryPrice,
        String status
) {

    public static BetResponse from(Bet bet) {
        return new BetResponse(
                bet.getId(),
                bet.getWalletId(),
                bet.getSymbol(),
                bet.getDirection().name(),
                bet.getStake().setScale(2),
                bet.getEntryPrice().setScale(2),
                bet.getStatus().name()
        );
    }
}
