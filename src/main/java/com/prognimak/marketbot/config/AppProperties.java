package com.prognimak.marketbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "market-bot")
public record AppProperties(
        String finnhubApiKey,
        String telegramBotToken,
        String telegramChatId,
        List<String> watchlist,
        double dropAlertPercent,
        double riseAlertPercent,
        long pollIntervalMs
) {}
