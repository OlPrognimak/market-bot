package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.dashboard.config.MarketDashboardProperties;
import com.prognimak.marketbot.dashboard.model.CryptoDashboardSnapshot;
import com.prognimak.marketbot.dashboard.model.CryptoScanResult;
import com.prognimak.marketbot.user.service.UserPropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class CryptoDashboardService {
    private final MarketDashboardProperties properties;
    private final UserPropertyService userPropertyService;
    private final Map<String, CryptoScanResult> latestResultsBySymbol = new ConcurrentHashMap<>();
    private volatile Instant lastScanAt;

    public void recordResult(CryptoScanResult result) {
        if (!properties.enabled()) {
            return;
        }
        latestResultsBySymbol.put(result.baseAsset(), result);
    }

    public CryptoDashboardSnapshot publishSnapshot(Instant scanAt) {
        lastScanAt = scanAt;
        return latestSnapshot();
    }

    public CryptoDashboardSnapshot latestSnapshot() {
        return buildSnapshot(null);
    }

    public CryptoDashboardSnapshot latestSnapshot(Long userId) {
        return buildSnapshot(userId);
    }

    private CryptoDashboardSnapshot buildSnapshot(Long userId) {
        Set<String> userSymbols = userId == null
                ? null
                : userPropertyService.loadUserCryptoSymbols(userId).orElse(null);

        List<CryptoScanResult> results = latestResultsBySymbol.values()
                .stream()
                .filter(result -> userSymbols == null || userSymbols.contains(result.baseAsset().toUpperCase(Locale.ROOT)))
                .sorted(Comparator
                        .comparingDouble(CryptoScanResult::movementScore)
                        .reversed()
                        .thenComparing(CryptoScanResult::updatedAt, Comparator.reverseOrder())
                        .thenComparing(CryptoScanResult::symbol))
                .limit(properties.maxResults())
                .toList();

        return new CryptoDashboardSnapshot(
                lastScanAt == null ? Instant.now() : lastScanAt,
                properties.scanTriggerMode(),
                new ArrayList<>(results),
                topPositive(results),
                topNegative(results)
        );
    }

    private CryptoScanResult topPositive(List<CryptoScanResult> results) {
        return results.stream()
                .filter(result -> result.priceChangePercent() > 0)
                .max(Comparator.comparingDouble(CryptoScanResult::priceChangePercent))
                .orElse(null);
    }

    private CryptoScanResult topNegative(List<CryptoScanResult> results) {
        return results.stream()
                .filter(result -> result.priceChangePercent() < 0)
                .min(Comparator.comparingDouble(CryptoScanResult::priceChangePercent))
                .orElse(null);
    }
}
