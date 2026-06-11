package com.prognimak.marketbot.dashboard.model;

import java.time.Instant;

public record MarketCandlePoint(
        Instant time,
        double open,
        double high,
        double low,
        double close,
        double volume
) {
}
