package com.cryptobet.engine.price.dto;

import java.math.BigDecimal;

public record PriceResponse(String symbol, String price) {

    public static PriceResponse from(String symbol, BigDecimal price) {
        return new PriceResponse(symbol, price.toPlainString());
    }
}
