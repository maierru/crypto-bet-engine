package com.cryptobet.engine.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;

@Component
public class BetWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(BetWebSocketHandler.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public BetWebSocketHandler(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void handleBetUpdate(BetUpdateEvent event) {
        publishBetUpdate(event);
    }

    public void publishBetUpdate(BetUpdateEvent event) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "bet_update");
        payload.put("betId", event.betId().toString());
        payload.put("status", event.status().name());
        payload.put("payout", event.payout().setScale(2, RoundingMode.HALF_UP).toPlainString());

        String json = payload.toString();
        messagingTemplate.convertAndSend("/topic/bets/" + event.walletId(), json);

        log.debug("Bet update pushed via WebSocket: bet={} status={}", event.betId(), event.status());
    }
}
