package com.cryptobet.engine.websocket;

import com.cryptobet.engine.bet.BetStatus;
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
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BetWebSocketTest {

    @LocalServerPort
    private int port;

    @Autowired
    private BetWebSocketHandler betWebSocketHandler;

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
    void receivesBetUpdateForSubscribedWallet() throws Exception {
        UUID walletId = UUID.randomUUID();
        UUID betId = UUID.randomUUID();
        BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();

        session.subscribe("/topic/bets/" + walletId, jsonHandler(messages));
        Thread.sleep(200);

        betWebSocketHandler.publishBetUpdate(new BetUpdateEvent(
                betId, walletId, BetStatus.WON, new BigDecimal("185.00")
        ));

        JsonNode json = messages.poll(5, TimeUnit.SECONDS);
        assertThat(json).isNotNull();
        assertThat(json.get("type").asText()).isEqualTo("bet_update");
        assertThat(json.get("betId").asText()).isEqualTo(betId.toString());
        assertThat(json.get("status").asText()).isEqualTo("WON");
        assertThat(json.get("payout").asText()).isEqualTo("185.00");
    }

    @Test
    void settlementTriggersPushWithBetIdStatusPayout() throws Exception {
        UUID walletId = UUID.randomUUID();
        UUID betId = UUID.randomUUID();
        BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();

        session.subscribe("/topic/bets/" + walletId, jsonHandler(messages));
        Thread.sleep(200);

        betWebSocketHandler.publishBetUpdate(new BetUpdateEvent(
                betId, walletId, BetStatus.LOST, BigDecimal.ZERO
        ));

        JsonNode json = messages.poll(5, TimeUnit.SECONDS);
        assertThat(json).isNotNull();
        assertThat(json.get("betId").asText()).isEqualTo(betId.toString());
        assertThat(json.get("status").asText()).isEqualTo("LOST");
        assertThat(json.get("payout").asText()).isEqualTo("0.00");
    }

    @Test
    void doesNotReceiveUpdatesForOtherWallets() throws Exception {
        UUID myWalletId = UUID.randomUUID();
        UUID otherWalletId = UUID.randomUUID();
        BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();

        session.subscribe("/topic/bets/" + myWalletId, jsonHandler(messages));
        Thread.sleep(200);

        betWebSocketHandler.publishBetUpdate(new BetUpdateEvent(
                UUID.randomUUID(), otherWalletId, BetStatus.WON, new BigDecimal("50.00")
        ));

        JsonNode message = messages.poll(2, TimeUnit.SECONDS);
        assertThat(message).isNull();
    }

    @Test
    void pushesOnAllStatusChanges() throws Exception {
        UUID walletId = UUID.randomUUID();
        BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();

        session.subscribe("/topic/bets/" + walletId, jsonHandler(messages));
        Thread.sleep(200);

        betWebSocketHandler.publishBetUpdate(new BetUpdateEvent(
                UUID.randomUUID(), walletId, BetStatus.OPEN, BigDecimal.ZERO
        ));
        betWebSocketHandler.publishBetUpdate(new BetUpdateEvent(
                UUID.randomUUID(), walletId, BetStatus.WON, new BigDecimal("100.00")
        ));
        betWebSocketHandler.publishBetUpdate(new BetUpdateEvent(
                UUID.randomUUID(), walletId, BetStatus.LOST, BigDecimal.ZERO
        ));
        betWebSocketHandler.publishBetUpdate(new BetUpdateEvent(
                UUID.randomUUID(), walletId, BetStatus.PUSH, new BigDecimal("50.00")
        ));
        betWebSocketHandler.publishBetUpdate(new BetUpdateEvent(
                UUID.randomUUID(), walletId, BetStatus.CANCELLED, BigDecimal.ZERO
        ));

        StringBuilder allMessages = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            JsonNode msg = messages.poll(5, TimeUnit.SECONDS);
            assertThat(msg).as("Expected message %d", i + 1).isNotNull();
            allMessages.append(msg.toString());
        }

        String combined = allMessages.toString();
        assertThat(combined).contains("\"OPEN\"");
        assertThat(combined).contains("\"WON\"");
        assertThat(combined).contains("\"LOST\"");
        assertThat(combined).contains("\"PUSH\"");
        assertThat(combined).contains("\"CANCELLED\"");
    }
}
