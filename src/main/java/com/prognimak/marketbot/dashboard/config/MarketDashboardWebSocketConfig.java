package com.prognimak.marketbot.dashboard.config;

import com.prognimak.marketbot.dashboard.websocket.MarketDashboardWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class MarketDashboardWebSocketConfig implements WebSocketConfigurer {
    private final MarketDashboardProperties properties;
    private final MarketDashboardWebSocketHandler handler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        if (!properties.enabled() || !properties.websocketEnabled()) {
            return;
        }

        registry.addHandler(handler, "/ws/market-dashboard")
                .setAllowedOriginPatterns("*");
    }
}
