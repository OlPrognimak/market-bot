package com.prognimak.marketbot.client;

import com.prognimak.marketbot.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class TelegramClient {

    private final WebClient webClient;
    private final AppProperties properties;

    public TelegramClient(WebClient.Builder builder, AppProperties properties) {
        this.webClient = builder
                .baseUrl("https://api.telegram.org")
                .build();
        this.properties = properties;
    }

    public void sendMessage(String text) {
        if (properties.telegramBotToken() == null || properties.telegramBotToken().isBlank()) {
            throw new IllegalStateException("Telegram bot token is not configured");
        }
        if (properties.telegramChatId() == null || properties.telegramChatId().isBlank()) {
            throw new IllegalStateException("Telegram chat id is not configured");
        }

        webClient.post()
                .uri("/bot{token}/sendMessage", properties.telegramBotToken())
                .bodyValue(new TelegramMessage(
                        properties.telegramChatId(),
                        text
                ))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private record TelegramMessage(
            String chat_id,
            String text
    ) {}
}
