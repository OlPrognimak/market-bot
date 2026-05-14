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
