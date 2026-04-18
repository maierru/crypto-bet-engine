package com.cryptobet.engine.price;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceUpdateEvent(String symbol, BigDecimal price, long timestamp) {

    public PriceUpdateEvent(String symbol, BigDecimal price) {
        this(symbol, price, Instant.now().toEpochMilli());
    }
}
