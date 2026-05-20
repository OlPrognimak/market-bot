package com.prognimak.marketbot.dashboard.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prognimak.marketbot.dashboard.model.MarketDashboardSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDashboardWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sessions.remove(session);
        session.close(CloseStatus.SERVER_ERROR);
    }

    public void broadcast(MarketDashboardSnapshot snapshot) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(snapshot);
        } catch (IOException e) {
            log.error("Could not serialize market dashboard snapshot", e);
            return;
        }

        TextMessage message = new TextMessage(payload);
        sessions.removeIf(session -> !send(session, message));
    }

    private boolean send(WebSocketSession session, TextMessage message) {
        if (!session.isOpen()) {
            return false;
        }

        try {
            session.sendMessage(message);
            return true;
        } catch (IOException e) {
            log.warn("Could not send market dashboard snapshot to websocket session {}", session.getId(), e);
            return false;
        }
    }
}
