package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.dashboard.model.ExtendedHoursResult;
import com.prognimak.marketbot.dashboard.model.ExtendedHoursSnapshot;
import com.prognimak.marketbot.model.MarketSession;
import com.prognimak.marketbot.user.service.UserPropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ExtendedHoursDashboardService {
    private final UserPropertyService userPropertyService;
    private final Map<String, ExtendedHoursResult> latest = new ConcurrentHashMap<>();
    private volatile Instant lastScanAt;

    public void record(ExtendedHoursResult result) {
        latest.put(result.symbol() + ":" + result.session(), result);
    }

    public void retainCurrentSession(String symbol, MarketSession session) {
        String normalizedSymbol = symbol.toUpperCase(Locale.ROOT);
        latest.entrySet().removeIf(entry ->
                entry.getValue().symbol().equalsIgnoreCase(normalizedSymbol)
                        && entry.getValue().session() != session
        );
        if (session != MarketSession.PRE_MARKET && session != MarketSession.POST_MARKET) {
            latest.entrySet().removeIf(entry -> entry.getValue().symbol().equalsIgnoreCase(normalizedSymbol));
        }
    }

    public void publish(Instant scanAt) {
        lastScanAt = scanAt;
    }

    public ExtendedHoursSnapshot snapshot(Long userId, MarketSession session) {
        Set<String> userSymbols = userId == null ? null : userPropertyService.loadUserStockSymbols(userId).orElse(null);
        List<ExtendedHoursResult> results = latest.values().stream()
                .filter(result -> session == null || result.session() == session)
                .filter(result -> userSymbols == null || userSymbols.contains(result.symbol().toUpperCase(Locale.ROOT)))
                .sorted(Comparator.comparingDouble((ExtendedHoursResult result) -> Math.abs(result.sessionMove())).reversed())
                .toList();
        return new ExtendedHoursSnapshot(lastScanAt == null ? Instant.now() : lastScanAt, results);
    }
}
