package com.cryptobet.engine.odds.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

public record OddsResponse(
        String symbol,
        String direction,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal odds,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal vigRate
) {}
