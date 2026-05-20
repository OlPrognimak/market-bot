package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.dashboard.config.MarketDashboardProperties;
import com.prognimak.marketbot.dashboard.model.MarketDashboardSnapshot;
import com.prognimak.marketbot.dashboard.model.MarketScanResult;
import com.prognimak.marketbot.dashboard.websocket.MarketDashboardWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class MarketDashboardService {
    private final MarketDashboardProperties properties;
    private final MarketDashboardWebSocketHandler webSocketHandler;
    private final Map<String, MarketScanResult> latestResultsBySymbol = new ConcurrentHashMap<>();
    private volatile MarketDashboardSnapshot latestSnapshot;

    public void recordResult(MarketScanResult result) {
        if (!properties.enabled()) {
            return;
        }

        latestResultsBySymbol.put(result.symbol(), result);
    }

    public MarketDashboardSnapshot publishSnapshot(Instant lastScanAt) {
        MarketDashboardSnapshot snapshot = buildSnapshot(lastScanAt);
        latestSnapshot = snapshot;

        if (properties.websocketEnabled()) {
            webSocketHandler.broadcast(snapshot);
        }

        return snapshot;
    }

    public MarketDashboardSnapshot latestSnapshot() {
        if (latestSnapshot == null) {
            latestSnapshot = buildSnapshot(Instant.now());
        }

        return latestSnapshot;
    }

    private MarketDashboardSnapshot buildSnapshot(Instant lastScanAt) {
        List<MarketScanResult> results = latestResultsBySymbol.values()
                .stream()
                .sorted(Comparator
                        .comparingDouble(MarketScanResult::movementScore)
                        .reversed()
                        .thenComparing(MarketScanResult::updatedAt, Comparator.reverseOrder())
                        .thenComparing(MarketScanResult::symbol))
                .limit(properties.maxResults())
                .toList();

        return new MarketDashboardSnapshot(
                lastScanAt,
                properties.scanTriggerMode(),
                new ArrayList<>(results),
                topPositiveRolling(results),
                topNegativeRolling(results),
                topPositiveDelta(results),
                topNegativeDelta(results)
        );
    }

    private MarketScanResult topPositiveRolling(List<MarketScanResult> results) {
        return results.stream()
                .filter(result -> result.rollingDelta() > 0)
                .max(Comparator.comparingDouble(MarketScanResult::rollingDelta))
                .orElse(null);
    }

    private MarketScanResult topNegativeRolling(List<MarketScanResult> results) {
        return results.stream()
                .filter(result -> result.rollingDelta() < 0)
                .min(Comparator.comparingDouble(MarketScanResult::rollingDelta))
                .orElse(null);
    }

    private MarketScanResult topPositiveDelta(List<MarketScanResult> results) {
        return results.stream()
                .filter(result -> result.delta() > 0)
                .max(Comparator.comparingDouble(MarketScanResult::delta))
                .orElse(null);
    }

    private MarketScanResult topNegativeDelta(List<MarketScanResult> results) {
        return results.stream()
                .filter(result -> result.delta() < 0)
                .min(Comparator.comparingDouble(MarketScanResult::delta))
                .orElse(null);
    }
}
