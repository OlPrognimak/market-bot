package com.prognimak.marketbot.dashboard.model;

import java.time.Instant;

public record CryptoScanResult(
        String symbol,
        String baseAsset,
        String coinName,
        String window,
        double openPrice,
        double closePrice,
        double priceChangePercent,
        double quoteVolume,
        MarketDirection direction,
        boolean alert,
        Instant updatedAt,
        String messageText
) {
    public double movementScore() {
        return Math.abs(priceChangePercent);
    }
}
