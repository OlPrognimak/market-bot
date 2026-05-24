package com.prognimak.marketbot.dashboard.model;

import java.time.Instant;

public record MarketChartPoint(
        Instant time,
        double price,
        double percentChange,
        double delta,
        double open,
        double high,
        double low,
        double previousClose
) {
}
