package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.dashboard.model.FuturesResult;
import com.prognimak.marketbot.dashboard.model.FuturesSnapshot;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FuturesDashboardService {
    private final Map<String, FuturesResult> latest = new ConcurrentHashMap<>();
    private volatile Instant lastScanAt;

    public void record(FuturesResult result) {
        latest.put(result.symbol(), result);
    }

    public FuturesSnapshot publish(Instant scanAt) {
        lastScanAt = scanAt;
        return snapshot();
    }

    public FuturesSnapshot snapshot() {
        List<FuturesResult> results = latest.values().stream()
                .sorted(Comparator.comparingDouble((FuturesResult result) -> Math.abs(result.changeFromSettlement())).reversed())
                .toList();
        return new FuturesSnapshot(
                lastScanAt == null ? Instant.now() : lastScanAt,
                results,
                results.stream().filter(r -> r.changeFromSettlement() > 0).max(Comparator.comparingDouble(FuturesResult::changeFromSettlement)).orElse(null),
                results.stream().filter(r -> r.changeFromSettlement() < 0).min(Comparator.comparingDouble(FuturesResult::changeFromSettlement)).orElse(null)
        );
    }
}
