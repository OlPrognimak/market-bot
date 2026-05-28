package com.prognimak.marketbot.dashboard.model;

import com.prognimak.marketbot.dashboard.config.ScanTriggerMode;

import java.time.Instant;
import java.util.List;

public record CryptoDashboardSnapshot(
        Instant lastScanAt,
        ScanTriggerMode triggerMode,
        List<CryptoScanResult> results,
        CryptoScanResult topPositive,
        CryptoScanResult topNegative
) {
}
