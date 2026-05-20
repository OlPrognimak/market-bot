package com.prognimak.marketbot.dashboard.model;

import com.prognimak.marketbot.dashboard.config.ScanTriggerMode;

import java.time.Instant;
import java.util.List;

public record MarketDashboardSnapshot(
        Instant lastScanAt,
        ScanTriggerMode triggerMode,
        List<MarketScanResult> results,
        MarketScanResult topPositiveRolling,
        MarketScanResult topNegativeRolling,
        MarketScanResult topPositiveDelta,
        MarketScanResult topNegativeDelta
) {
}
