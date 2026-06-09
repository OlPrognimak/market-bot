package com.prognimak.marketbot.dashboard.model;

import java.time.Instant;
import java.util.List;

public record FuturesSnapshot(Instant lastScanAt, List<FuturesResult> results, FuturesResult topPositive, FuturesResult topNegative) {
}
