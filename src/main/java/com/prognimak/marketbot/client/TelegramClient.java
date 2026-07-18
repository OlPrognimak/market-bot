package com.prognimak.marketbot.client;

import com.prognimak.marketbot.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class TelegramClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

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

        send(properties.messageSender().telegramBotToken(), properties.messageSender().telegramChatId(), text);
    }

    public void sendMessage(String botToken, String chatId, String text) {
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalStateException("Telegram bot token is not configured");
        }
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalStateException("Telegram chat id is not configured");
        }

        send(botToken, chatId, text);
    }

    private void send(String botToken, String chatId, String text) {
        try {
            webClient.post()
                    .uri("/bot{token}/sendMessage", botToken)
                    .bodyValue(new TelegramMessage(
                            chatId,
                            text
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block(REQUEST_TIMEOUT.plusSeconds(1));
        } catch (RuntimeException e) {
            throw new TelegramDeliveryException("Telegram send failed: " + sanitize(e.getMessage()), e);
        }
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "request failed";
        }
        return message.replaceAll("/bot[^/\\s]+/sendMessage", "/bot***/sendMessage");
    }

    public static class TelegramDeliveryException extends RuntimeException {
        public TelegramDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record TelegramMessage(
            String chat_id,
            String text
    ) {}
}
