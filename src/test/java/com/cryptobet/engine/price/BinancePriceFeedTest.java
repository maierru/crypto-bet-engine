package com.cryptobet.engine.price;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BinancePriceFeedTest {

    private PriceService priceService;
    private BinancePriceFeed priceFeed;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        priceService = new PriceService(event -> {});
        // Create feed with auto-connect disabled (no symbols)
        priceFeed = new BinancePriceFeed(priceService, objectMapper, "wss://stream.binance.com:9443/ws", "");
    }

    @Test
    void handleMessage_validMiniTicker_updatesPrice() {
        String message = """
                {"e":"24hrMiniTicker","E":1672515782136,"s":"BTCUSDT","c":"65000.50000000","o":"64000.00","h":"66000.00","l":"63000.00","v":"1234.56","q":"80000000.00"}
                """;

        priceFeed.handleMessage(message);

        var price = priceService.getPrice("BTCUSDT");
        assertTrue(price.isPresent());
        assertEquals(new BigDecimal("65000.50000000"), price.get());
    }

    @Test
    void handleMessage_multipleMessages_updatesAll() {
        priceFeed.handleMessage("""
                {"e":"24hrMiniTicker","s":"BTCUSDT","c":"65000.00"}
                """);
        priceFeed.handleMessage("""
                {"e":"24hrMiniTicker","s":"ETHUSDT","c":"3500.00"}
                """);

        assertEquals(new BigDecimal("65000.00"), priceService.getPrice("BTCUSDT").orElseThrow());
        assertEquals(new BigDecimal("3500.00"), priceService.getPrice("ETHUSDT").orElseThrow());
    }

    @Test
    void handleMessage_invalidJson_doesNotThrow() {
        assertDoesNotThrow(() -> priceFeed.handleMessage("not json"));
    }

    @Test
    void handleMessage_missingFields_doesNotThrow() {
        assertDoesNotThrow(() -> priceFeed.handleMessage("""
                {"e":"24hrMiniTicker"}
                """));
    }

    @Test
    void handleMessage_combinedStream_updatesPrice() {
        // Combined stream wraps data in {"stream":"...","data":{...}}
        String message = """
                {"stream":"btcusdt@miniTicker","data":{"e":"24hrMiniTicker","s":"BTCUSDT","c":"65000.50"}}
                """;

        priceFeed.handleMessage(message);

        assertEquals(new BigDecimal("65000.50"), priceService.getPrice("BTCUSDT").orElseThrow());
    }
}
