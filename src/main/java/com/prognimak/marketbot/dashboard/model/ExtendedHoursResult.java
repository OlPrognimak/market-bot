package com.prognimak.marketbot.dashboard.model;

import com.prognimak.marketbot.model.FreshnessStatus;
import com.prognimak.marketbot.model.MarketSession;

import java.time.Instant;

public record ExtendedHoursResult(
        String symbol,
        String companyName,
        String region,
        String sector,
        String exchange,
        MarketSession session,
        double price,
        double sessionMove,
        double delta,
        double rolling,
        double volume,
        FreshnessStatus freshness,
        Instant providerTimestamp
) {
}
