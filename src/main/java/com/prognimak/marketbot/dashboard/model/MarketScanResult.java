package com.prognimak.marketbot.dashboard.model;

import java.time.Instant;

public record MarketScanResult(
        String symbol,
        String companyName,
        double currentPercent,
        double previousPercent,
        double delta,
        double rollingDelta,
        int rollingWindowSize,
        double currentPrice,
        double low,
        double high,
        double open,
        double previousClose,
        MarketDirection direction,
        boolean alert,
        Instant updatedAt,
        String messageText
) {
    public double movementScore() {
        return Math.max(Math.abs(delta), Math.abs(rollingDelta));
    }
}
