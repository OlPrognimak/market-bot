package com.prognimak.marketbot.dashboard.model;

import java.time.Instant;
import java.util.List;

public record MarketChartResponse(
        String symbol,
        String range,
        Instant requestedFrom,
        Instant actualFrom,
        Instant actualTo,
        boolean fallback,
        List<MarketChartPoint> points
) {
}
