package com.cryptobet.engine.price;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PriceService {

    private final ConcurrentHashMap<String, BigDecimal> prices = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;

    public PriceService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void updatePrice(String symbol, BigDecimal price) {
        prices.put(symbol, price);
        eventPublisher.publishEvent(new PriceUpdateEvent(symbol, price));
    }

    public Optional<BigDecimal> getPrice(String symbol) {
        return Optional.ofNullable(prices.get(symbol));
    }

    public Map<String, BigDecimal> getAllPrices() {
        return Map.copyOf(prices);
    }
}
