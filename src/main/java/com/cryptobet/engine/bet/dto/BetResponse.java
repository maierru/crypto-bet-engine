package com.cryptobet.engine.bet.dto;

import com.cryptobet.engine.bet.Bet;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BetResponse(
        UUID id,
        UUID walletId,
        String symbol,
        String direction,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal stake,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal entryPrice,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal odds,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal potentialPayout,
        Instant resolveAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal priceAtResolution,
        Instant resolvedAt,
        String status
) {

    public static BetResponse from(Bet bet) {
        return new BetResponse(
                bet.getId(),
                bet.getWalletId(),
                bet.getSymbol(),
                bet.getDirection().name(),
                bet.getStake().setScale(2, RoundingMode.HALF_UP),
                bet.getEntryPrice().setScale(2, RoundingMode.HALF_UP),
                bet.getOdds().setScale(4, RoundingMode.HALF_UP),
                bet.getPotentialPayout().setScale(2, RoundingMode.HALF_UP),
                bet.getResolveAt(),
                bet.getPriceAtResolution() != null ? bet.getPriceAtResolution().setScale(2, RoundingMode.HALF_UP) : null,
                bet.getResolvedAt(),
                bet.getStatus().name()
        );
    }
}
