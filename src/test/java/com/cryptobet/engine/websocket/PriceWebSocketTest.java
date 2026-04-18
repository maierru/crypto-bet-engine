package com.cryptobet.engine.websocket;

import com.cryptobet.engine.price.PriceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.util.List;

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

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;
    private StompSession session;

    @BeforeEach
    void setUp() throws Exception {
        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        WebSocketClient sockJsClient = new SockJsClient(transports);
        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new StringMessageConverter());

        session = stompClient.connectAsync(
                "http://localhost:" + port + "/ws",
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

    @Test
    void connectsToWebSocketEndpoint() {
        assertThat(session.isConnected()).isTrue();
    }

    @Test
    void receivesPriceUpdateOnTopic() throws Exception {
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        session.subscribe("/topic/prices", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((String) payload);
            }
        });

        // Allow subscription to register
        Thread.sleep(200);

        priceService.updatePrice("BTCUSDT", new BigDecimal("65432.10"));

        String message = messages.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull();

        JsonNode json = objectMapper.readTree(message);
        assertThat(json.get("symbol").asText()).isEqualTo("BTCUSDT");
        assertThat(json.get("price").asText()).isEqualTo("65432.10");
    }

    @Test
    void receivesPriceUpdatesForMultipleSymbols() throws Exception {
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        session.subscribe("/topic/prices", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((String) payload);
            }
        });

        Thread.sleep(200);

        priceService.updatePrice("BTCUSDT", new BigDecimal("65000.00"));
        priceService.updatePrice("ETHUSDT", new BigDecimal("3500.50"));

        String msg1 = messages.poll(5, TimeUnit.SECONDS);
        String msg2 = messages.poll(5, TimeUnit.SECONDS);

        assertThat(msg1).isNotNull();
        assertThat(msg2).isNotNull();

        // Both symbols should be received (order may vary)
        String combined = msg1 + msg2;
        assertThat(combined).contains("BTCUSDT");
        assertThat(combined).contains("ETHUSDT");
    }

    @Test
    void receivesPriceUpdateOnSymbolSpecificTopic() throws Exception {
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        session.subscribe("/topic/prices/BTCUSDT", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((String) payload);
            }
        });

        Thread.sleep(200);

        priceService.updatePrice("BTCUSDT", new BigDecimal("65432.10"));
        priceService.updatePrice("ETHUSDT", new BigDecimal("3500.00")); // Should NOT appear

        String message = messages.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull();

        JsonNode json = objectMapper.readTree(message);
        assertThat(json.get("symbol").asText()).isEqualTo("BTCUSDT");

        // Should not receive ETH update on BTC-specific topic
        String noMessage = messages.poll(1, TimeUnit.SECONDS);
        assertThat(noMessage).isNull();
    }

    @Test
    void priceUpdateContainsTimestamp() throws Exception {
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        session.subscribe("/topic/prices", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((String) payload);
            }
        });

        Thread.sleep(200);

        priceService.updatePrice("BTCUSDT", new BigDecimal("65432.10"));

        String message = messages.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull();

        JsonNode json = objectMapper.readTree(message);
        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.get("timestamp").asLong()).isGreaterThan(0);
    }
}
