package com.prognimak.marketbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "market-bot")
public record AppProperties(
        String finnhubApiKey,
        String telegramBotToken,
        String telegramChatId,
        String twelveDataApiKey,
        Map<String, String> watchlist,
        double dropAlertPercent,
        double riseAlertPercent,
        long pollIntervalMs,
        double maximalDeltaPrice

) {}
