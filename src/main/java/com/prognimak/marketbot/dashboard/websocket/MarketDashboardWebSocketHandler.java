package com.prognimak.marketbot.dashboard.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prognimak.marketbot.dashboard.model.MarketDashboardSnapshot;
import com.prognimak.marketbot.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDashboardWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final Map<WebSocketSession, Optional<Long>> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session, userId(session));
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
        broadcast(ignored -> snapshot);
    }

    public void broadcast(Function<Long, MarketDashboardSnapshot> snapshotFactory) {
        sessions.entrySet().removeIf(entry -> !send(entry.getKey(), snapshotFactory.apply(entry.getValue().orElse(null))));
    }

    private Optional<Long> userId(WebSocketSession session) {
        return accessToken(session.getUri()).flatMap(jwtService::extractUserId);
    }

    private Optional<String> accessToken(URI uri) {
        if (uri == null || uri.getRawQuery() == null || uri.getRawQuery().isBlank()) {
            return Optional.empty();
        }

        for (String parameter : uri.getRawQuery().split("&")) {
            String[] parts = parameter.split("=", 2);
            if (parts.length == 2 && "access_token".equals(parts[0])) {
                return Optional.of(URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
        }
        return Optional.empty();
    }

    private boolean send(WebSocketSession session, MarketDashboardSnapshot snapshot) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(snapshot);
        } catch (IOException e) {
            log.error("Could not serialize market dashboard snapshot", e);
            return false;
        }

        return send(session, new TextMessage(payload));
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
