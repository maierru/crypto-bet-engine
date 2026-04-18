package com.cryptobet.engine.websocket;

import com.cryptobet.engine.price.PriceUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class PriceWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PriceWebSocketHandler.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public PriceWebSocketHandler(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void handlePriceUpdate(PriceUpdateEvent event) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("symbol", event.symbol());
        payload.put("price", event.price().toPlainString());
        payload.put("timestamp", event.timestamp());

        String json = payload.toString();

        messagingTemplate.convertAndSend("/topic/prices", json);
        messagingTemplate.convertAndSend("/topic/prices/" + event.symbol(), json);

        log.debug("Price update pushed via WebSocket: {} = {}", event.symbol(), event.price());
    }
}
