package com.cryptobet.engine.price;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class BinancePriceFeed {

    private static final Logger log = LoggerFactory.getLogger(BinancePriceFeed.class);

    private final PriceService priceService;
    private final ObjectMapper objectMapper;
    private final String wsBaseUrl;
    private final List<String> symbols;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private volatile WebSocket webSocket;
    private volatile boolean running = false;

    public BinancePriceFeed(
            PriceService priceService,
            ObjectMapper objectMapper,
            @Value("${binance.ws-url:wss://stream.binance.com:9443/ws}") String wsBaseUrl,
            @Value("${binance.symbols:}") String symbolsCsv) {
        this.priceService = priceService;
        this.objectMapper = objectMapper;
        this.wsBaseUrl = wsBaseUrl;
        this.symbols = symbolsCsv.isBlank()
                ? List.of()
                : Arrays.stream(symbolsCsv.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    @PostConstruct
    public void start() {
        if (symbols.isEmpty()) {
            log.info("No symbols configured for Binance price feed — skipping connection");
            return;
        }
        running = true;
        connect();
    }

    @PreDestroy
    public void stop() {
        running = false;
        scheduler.shutdownNow();
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
        }
    }

    private void connect() {
        if (!running) return;

        String streams = symbols.stream()
                .map(s -> s.toLowerCase() + "@miniTicker")
                .reduce((a, b) -> a + "/" + b)
                .orElseThrow();

        String url = wsBaseUrl.replace("/ws", "/stream?streams=" + streams);
        log.info("Connecting to Binance WebSocket: {}", url);

        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(url), new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket ws) {
                        log.info("Binance WebSocket connected");
                        webSocket = ws;
                        ws.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        buffer.append(data);
                        if (last) {
                            handleMessage(buffer.toString());
                            buffer.setLength(0);
                        }
                        ws.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                        log.warn("Binance WebSocket closed: {} {}", statusCode, reason);
                        scheduleReconnect();
                        return null;
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        log.error("Binance WebSocket error", error);
                        scheduleReconnect();
                    }
                })
                .exceptionally(ex -> {
                    log.error("Failed to connect to Binance WebSocket", ex);
                    scheduleReconnect();
                    return null;
                });
    }

    private void scheduleReconnect() {
        if (running) {
            log.info("Scheduling Binance WebSocket reconnect in 5 seconds");
            scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
        }
    }

    void handleMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);

            // Combined stream format: {"stream":"...","data":{...}}
            JsonNode ticker = root.has("data") ? root.get("data") : root;

            String symbol = ticker.has("s") ? ticker.get("s").asText() : null;
            String closePrice = ticker.has("c") ? ticker.get("c").asText() : null;

            if (symbol != null && closePrice != null) {
                priceService.updatePrice(symbol, new BigDecimal(closePrice));
                log.debug("Price update: {} = {}", symbol, closePrice);
            }
        } catch (Exception e) {
            log.warn("Failed to parse Binance message: {}", e.getMessage());
        }
    }
}
