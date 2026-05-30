package com.prognimak.marketbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "market-bot")
public record AppProperties(
        ProviderConfig providers,
        MessageSenderConfig messageSender,
        ScannerConfig scanner,
        SharesConfig shares,
        AlertConfig alert,
        CryptoConfig crypto
) {
    public record ProviderConfig(
            String finnhubApiKey,
            String twelveDataApiKey
    ) {
    }

    public record MessageSenderConfig(
            String telegramBotToken,
            String telegramChatId,
            int maxConcurrentSends
    ) {
    }

    public record ScannerConfig(
            long pollIntervalMs,
            double quoteChangeEpsilon,
            double maxChangesForPersist,
            int maxQuoteFetchAttempts,
            long quoteFetchRetryDelayMs
    ) {
    }

    public record SharesConfig(
            String watchlistFile,
            Map<String, String> watchlist
    ) {
    }

    public record AlertConfig(
            double dropAlertPercent,
            double riseAlertPercent,
            double maximalRollingPrice,
            int maximalRollingSize
    ) {
    }

    public record CryptoConfig(
            boolean scannerEnabled,
            long pollIntervalMs,
            String watchlistFile,
            Map<String, String> watchlist,
            double minQuoteVolume,
            double priceChangePercent,
            String scanWindow,
            int maxQuoteFetchAttempts,
            long quoteFetchRetryDelayMs
    ) {
    }
}
