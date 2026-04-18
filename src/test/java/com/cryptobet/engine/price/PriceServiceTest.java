package com.cryptobet.engine.price;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PriceServiceTest {

    private PriceService priceService;

    @BeforeEach
    void setUp() {
        priceService = new PriceService();
    }

    @Test
    void updatePrice_storesPrice() {
        priceService.updatePrice("BTCUSDT", new BigDecimal("65000.50"));

        Optional<BigDecimal> price = priceService.getPrice("BTCUSDT");
        assertTrue(price.isPresent());
        assertEquals(new BigDecimal("65000.50"), price.get());
    }

    @Test
    void getPrice_unknownSymbol_returnsEmpty() {
        Optional<BigDecimal> price = priceService.getPrice("UNKNOWN");
        assertTrue(price.isEmpty());
    }

    @Test
    void updatePrice_overwritesPrevious() {
        priceService.updatePrice("ETHUSDT", new BigDecimal("3500.00"));
        priceService.updatePrice("ETHUSDT", new BigDecimal("3550.25"));

        Optional<BigDecimal> price = priceService.getPrice("ETHUSDT");
        assertTrue(price.isPresent());
        assertEquals(new BigDecimal("3550.25"), price.get());
    }

    @Test
    void updatePrice_multipleSymbols_trackedIndependently() {
        priceService.updatePrice("BTCUSDT", new BigDecimal("65000.00"));
        priceService.updatePrice("ETHUSDT", new BigDecimal("3500.00"));

        assertEquals(new BigDecimal("65000.00"), priceService.getPrice("BTCUSDT").orElseThrow());
        assertEquals(new BigDecimal("3500.00"), priceService.getPrice("ETHUSDT").orElseThrow());
    }

    @Test
    void getPrice_caseInsensitive_usesExactKey() {
        priceService.updatePrice("BTCUSDT", new BigDecimal("65000.00"));

        // Keys are case-sensitive — lowercase should not match
        assertTrue(priceService.getPrice("btcusdt").isEmpty());
    }

    @Test
    void getAllPrices_returnsAllTrackedPrices() {
        priceService.updatePrice("BTCUSDT", new BigDecimal("65000.00"));
        priceService.updatePrice("ETHUSDT", new BigDecimal("3500.00"));

        var prices = priceService.getAllPrices();
        assertEquals(2, prices.size());
        assertEquals(new BigDecimal("65000.00"), prices.get("BTCUSDT"));
        assertEquals(new BigDecimal("3500.00"), prices.get("ETHUSDT"));
    }
}
