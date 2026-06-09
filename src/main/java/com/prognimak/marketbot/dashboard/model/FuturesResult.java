package com.prognimak.marketbot.dashboard.model;

import com.prognimak.marketbot.model.FreshnessStatus;

import java.time.Instant;

public record FuturesResult(
        String symbol,
        String name,
        String underlying,
        String region,
        String exchange,
        String currency,
        double price,
        double changeFromSettlement,
        double changeFromOpen,
        double delta,
        double rolling,
        double volume,
        FreshnessStatus freshness,
        Instant providerTimestamp
) {
}
