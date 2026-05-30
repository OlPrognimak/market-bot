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
        if (properties.messageSender().telegramBotToken() == null || properties.messageSender().telegramBotToken().isBlank()) {
            throw new IllegalStateException("Telegram bot token is not configured");
        }
        if (properties.messageSender().telegramChatId() == null || properties.messageSender().telegramChatId().isBlank()) {
            throw new IllegalStateException("Telegram chat id is not configured");
        }

        webClient.post()
                .uri("/bot{token}/sendMessage", properties.messageSender().telegramBotToken())
                .bodyValue(new TelegramMessage(
                        properties.messageSender().telegramChatId(),
                        text
                ))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void sendMessage(String botToken, String chatId, String text) {
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalStateException("Telegram bot token is not configured");
        }
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalStateException("Telegram chat id is not configured");
        }

        webClient.post()
                .uri("/bot{token}/sendMessage", botToken)
                .bodyValue(new TelegramMessage(
                        chatId,
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
