package com.prognimak.marketbot.dashboard.model;

import java.time.Instant;
import java.util.List;

public record MarketCandleResponse(
        String symbol,
        String range,
        String interval,
        String timeZone,
        Instant requestedFrom,
        Instant actualFrom,
        Instant actualTo,
        boolean fallback,
        List<MarketCandlePoint> points
) {
}
