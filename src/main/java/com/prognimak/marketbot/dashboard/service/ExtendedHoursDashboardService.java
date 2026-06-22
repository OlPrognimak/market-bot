package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.dashboard.model.ExtendedHoursResult;
import com.prognimak.marketbot.dashboard.model.ExtendedHoursSnapshot;
import com.prognimak.marketbot.entity.ShareSessionQuoteEntity;
import com.prognimak.marketbot.model.FreshnessStatus;
import com.prognimak.marketbot.model.MarketSession;
import com.prognimak.marketbot.model.WatchlistItem;
import com.prognimak.marketbot.repository.ShareSessionQuoteRepository;
import com.prognimak.marketbot.service.WatchlistService;
import com.prognimak.marketbot.user.service.UserPropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExtendedHoursDashboardService {
    private static final ZoneId DEFAULT_MARKET_ZONE = ZoneId.of("America/New_York");

    private final UserPropertyService userPropertyService;
    private final ShareSessionQuoteRepository repository;
    private final WatchlistService watchlistService;
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

    @Transactional(readOnly = true)
    public ExtendedHoursSnapshot snapshot(Long userId, MarketSession session) {
        Set<String> userSymbols = userId == null ? null : userPropertyService.loadUserStockSymbols(userId).orElse(null);
        List<ExtendedHoursResult> results = latest.values().stream()
                .filter(result -> session == null || result.session() == session)
                .filter(result -> userSymbols == null || userSymbols.contains(result.symbol().toUpperCase(Locale.ROOT)))
                .sorted(Comparator.comparingDouble((ExtendedHoursResult result) -> Math.abs(result.sessionMove())).reversed())
                .toList();
        if (results.isEmpty()) {
            results = fallbackResults(session, userSymbols);
        } else {
            LocalDate inMemoryDate = dataDate(results, DEFAULT_MARKET_ZONE);
            ShareSessionQuoteEntity latestPersisted = latestEntity(session, userSymbols);
            if (latestPersisted != null) {
                ZoneId marketZone = marketZone(latestPersisted.getExchangeTimezone());
                LocalDate persistedDate = latestPersisted.getProviderTimestamp().atZone(marketZone).toLocalDate();
                if (inMemoryDate == null || persistedDate.isAfter(inMemoryDate)) {
                    results = fallbackResults(session, userSymbols);
                }
            }
        }
        LocalDate dataDate = dataDate(results, DEFAULT_MARKET_ZONE);
        LocalDate expectedDate = expectedDate(session);
        boolean fallback = dataDate != null && !dataDate.equals(expectedDate);
        return new ExtendedHoursSnapshot(lastScanAt == null ? Instant.now() : lastScanAt, dataDate, expectedDate, fallback, results);
    }

    private LocalDate dataDate(List<ExtendedHoursResult> results, ZoneId marketZone) {
        return results.stream()
                .map(ExtendedHoursResult::providerTimestamp)
                .max(Comparator.naturalOrder())
                .map(timestamp -> timestamp.atZone(marketZone).toLocalDate())
                .orElse(null);
    }

    private LocalDate expectedDate(MarketSession session) {
        LocalDate today = LocalDate.now(DEFAULT_MARKET_ZONE);
        if (session == MarketSession.POST_MARKET) {
            return previousBusinessDay(today);
        }
        return businessDayOrPrevious(today);
    }

    private LocalDate businessDayOrPrevious(LocalDate date) {
        LocalDate candidate = date;
        while (isWeekend(candidate)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }

    private LocalDate previousBusinessDay(LocalDate date) {
        LocalDate candidate = date.minusDays(1);
        while (isWeekend(candidate)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek().getValue() >= 6;
    }

    private List<ExtendedHoursResult> fallbackResults(MarketSession session, Set<String> userSymbols) {
        ShareSessionQuoteEntity latestEntity = latestEntity(session, userSymbols);
        if (latestEntity == null) {
            return List.of();
        }

        ZoneId marketZone = marketZone(latestEntity.getExchangeTimezone());
        LocalDate latestDate = latestEntity.getProviderTimestamp().atZone(marketZone).toLocalDate();
        Instant from = latestDate.atStartOfDay(marketZone).toInstant();
        Instant to = latestDate.plusDays(1).atStartOfDay(marketZone).toInstant();
        List<ShareSessionQuoteEntity> entities = session == null
                ? repository.findByProviderTimestampBetweenOrderByProviderTimestampDesc(from, to)
                : repository.findBySessionTypeAndProviderTimestampBetweenOrderByProviderTimestampDesc(session, from, to);
        Map<String, WatchlistItem> watchlist = watchlistService.watchlist().values().stream()
                .collect(Collectors.toMap(
                        item -> item.symbol().toUpperCase(Locale.ROOT),
                        Function.identity(),
                        (first, ignored) -> first
                ));

        return entities.stream()
                .filter(entity -> userSymbols == null || userSymbols.contains(entity.getSymbol().toUpperCase(Locale.ROOT)))
                .collect(Collectors.toMap(
                        entity -> entity.getSymbol().toUpperCase(Locale.ROOT) + ":" + entity.getSessionType(),
                        Function.identity(),
                        (newest, ignored) -> newest,
                        LinkedHashMap::new
                ))
                .values().stream()
                .map(entity -> toFallbackResult(entity, watchlist.get(entity.getSymbol().toUpperCase(Locale.ROOT))))
                .sorted(Comparator.comparingDouble((ExtendedHoursResult result) -> Math.abs(result.sessionMove())).reversed())
                .toList();
    }

    private ShareSessionQuoteEntity latestEntity(MarketSession session, Set<String> userSymbols) {
        if (userSymbols != null && userSymbols.isEmpty()) {
            return null;
        }
        if (userSymbols != null) {
            return session == null
                    ? repository.findFirstBySymbolInOrderByProviderTimestampDesc(userSymbols).orElse(null)
                    : repository.findFirstBySymbolInAndSessionTypeOrderByProviderTimestampDesc(userSymbols, session).orElse(null);
        }
        return session == null
                ? repository.findFirstByOrderByProviderTimestampDesc().orElse(null)
                : repository.findFirstBySessionTypeOrderByProviderTimestampDesc(session).orElse(null);
    }

    private ExtendedHoursResult toFallbackResult(ShareSessionQuoteEntity entity, WatchlistItem item) {
        return new ExtendedHoursResult(
                entity.getSymbol(),
                item == null ? entity.getSymbol() : item.name(),
                item == null ? null : item.region(),
                item == null ? null : item.sector(),
                entity.getExchange(),
                entity.getSessionType(),
                entity.getPrice(),
                entity.getChangePercent(),
                entity.getDeltaPercent(),
                entity.getRollingPercent(),
                entity.getVolume(),
                FreshnessStatus.STALE,
                entity.getProviderTimestamp()
        );
    }

    private ZoneId marketZone(String exchangeTimezone) {
        if (exchangeTimezone == null || exchangeTimezone.isBlank()) {
            return DEFAULT_MARKET_ZONE;
        }
        try {
            return ZoneId.of(exchangeTimezone);
        } catch (RuntimeException ignored) {
            return DEFAULT_MARKET_ZONE;
        }
    }
}
