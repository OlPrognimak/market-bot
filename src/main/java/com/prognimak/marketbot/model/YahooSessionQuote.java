package com.prognimak.marketbot.model;

import java.time.Instant;

public record YahooSessionQuote(
        String symbol,
        MarketSession session,
        MarketSession activeSession,
        String exchange,
        String exchangeTimezone,
        double price,
        double baselinePrice,
        double changePercent,
        double open,
        double high,
        double low,
        double volume,
        Instant providerTimestamp
) {
}
