package com.cryptobet.engine.websocket;

import com.cryptobet.engine.price.PriceService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PriceWebSocketTest {

    @LocalServerPort
    private int port;

    @Autowired
    private PriceService priceService;

    private WebSocketStompClient stompClient;
    private StompSession session;

    @BeforeEach
    void setUp() throws Exception {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        session = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    private StompFrameHandler jsonHandler(BlockingQueue<JsonNode> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((JsonNode) payload);
            }
        };
    }

    @Test
    void connectsToWebSocketEndpoint() {
        assertThat(session.isConnected()).isTrue();
    }

    @Test
    void receivesPriceUpdateOnTopic() throws Exception {
        BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        session.subscribe("/topic/prices", jsonHandler(messages));
        Thread.sleep(200);

        priceService.updatePrice("BTCUSDT", new BigDecimal("65432.10"));

        JsonNode json = messages.poll(5, TimeUnit.SECONDS);
        assertThat(json).isNotNull();
        assertThat(json.get("symbol").asText()).isEqualTo("BTCUSDT");
        assertThat(json.get("price").asText()).isEqualTo("65432.10");
    }

    @Test
    void receivesPriceUpdatesForMultipleSymbols() throws Exception {
        BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        session.subscribe("/topic/prices", jsonHandler(messages));
        Thread.sleep(200);

        priceService.updatePrice("BTCUSDT", new BigDecimal("65000.00"));
        priceService.updatePrice("ETHUSDT", new BigDecimal("3500.50"));

        JsonNode msg1 = messages.poll(5, TimeUnit.SECONDS);
        JsonNode msg2 = messages.poll(5, TimeUnit.SECONDS);

        assertThat(msg1).isNotNull();
        assertThat(msg2).isNotNull();

        String combined = msg1.toString() + msg2.toString();
        assertThat(combined).contains("BTCUSDT");
        assertThat(combined).contains("ETHUSDT");
    }

    @Test
    void receivesPriceUpdateOnSymbolSpecificTopic() throws Exception {
        BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        session.subscribe("/topic/prices/BTCUSDT", jsonHandler(messages));
        Thread.sleep(200);

        priceService.updatePrice("BTCUSDT", new BigDecimal("65432.10"));
        priceService.updatePrice("ETHUSDT", new BigDecimal("3500.00"));

        JsonNode json = messages.poll(5, TimeUnit.SECONDS);
        assertThat(json).isNotNull();
        assertThat(json.get("symbol").asText()).isEqualTo("BTCUSDT");

        JsonNode noMessage = messages.poll(1, TimeUnit.SECONDS);
        assertThat(noMessage).isNull();
    }

    @Test
    void priceUpdateContainsTimestamp() throws Exception {
        BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        session.subscribe("/topic/prices", jsonHandler(messages));
        Thread.sleep(200);

        priceService.updatePrice("BTCUSDT", new BigDecimal("65432.10"));

        JsonNode json = messages.poll(5, TimeUnit.SECONDS);
        assertThat(json).isNotNull();
        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.get("timestamp").asLong()).isGreaterThan(0);
    }
}
