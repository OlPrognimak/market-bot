package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.dashboard.model.ExtendedHoursResult;
import com.prognimak.marketbot.entity.ShareSessionQuoteEntity;
import com.prognimak.marketbot.model.FreshnessStatus;
import com.prognimak.marketbot.model.MarketSession;
import com.prognimak.marketbot.model.WatchlistItem;
import com.prognimak.marketbot.repository.ShareSessionQuoteRepository;
import com.prognimak.marketbot.service.WatchlistService;
import com.prognimak.marketbot.user.service.UserPropertyService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExtendedHoursDashboardServiceTest {

    private final ShareSessionQuoteRepository repository = mock(ShareSessionQuoteRepository.class);
    private final WatchlistService watchlistService = mock(WatchlistService.class);
    private final ExtendedHoursDashboardService service = new ExtendedHoursDashboardService(
            mock(UserPropertyService.class),
            repository,
            watchlistService
    );

    @Test
    void removesPreviousExtendedSessionWhenCurrentSessionChanges() {
        service.record(result(MarketSession.POST_MARKET));

        service.retainCurrentSession("AAPL", MarketSession.PRE_MARKET);

        assertEquals(0, service.snapshot(null, MarketSession.POST_MARKET).results().size());
    }

    @Test
    void removesExtendedHoursRowsDuringRegularSession() {
        service.record(result(MarketSession.PRE_MARKET));
        service.record(result(MarketSession.POST_MARKET));

        service.retainCurrentSession("AAPL", MarketSession.REGULAR);

        assertEquals(0, service.snapshot(null, null).results().size());
    }

    @Test
    void fallsBackToLatestAvailableSessionDate() {
        ShareSessionQuoteEntity newest = entity("AAPL", Instant.parse("2026-06-09T23:30:00Z"));
        ShareSessionQuoteEntity olderSameDate = entity("AAPL", Instant.parse("2026-06-09T22:30:00Z"));
        ShareSessionQuoteEntity secondSymbol = entity("MSFT", Instant.parse("2026-06-09T23:00:00Z"));
        when(repository.findFirstBySessionTypeOrderByProviderTimestampDesc(MarketSession.POST_MARKET))
                .thenReturn(Optional.of(newest));
        when(repository.findBySessionTypeAndProviderTimestampBetweenOrderByProviderTimestampDesc(
                eq(MarketSession.POST_MARKET), any(), any()
        )).thenReturn(List.of(newest, secondSymbol, olderSameDate));
        when(watchlistService.watchlist()).thenReturn(Map.of(
                "AAPL", WatchlistItem.simple("AAPL", "Apple"),
                "MSFT", WatchlistItem.simple("MSFT", "Microsoft")
        ));

        var snapshot = service.snapshot(null, MarketSession.POST_MARKET);

        assertTrue(snapshot.fallback());
        assertEquals(LocalDate.of(2026, 6, 9), snapshot.dataDate());
        assertEquals(2, snapshot.results().size());
        assertTrue(snapshot.results().stream().allMatch(result -> result.freshness() == FreshnessStatus.STALE));
    }

    private ExtendedHoursResult result(MarketSession session) {
        return new ExtendedHoursResult(
                "AAPL", "Apple", "US", "Technology", "NMS", session,
                200, 1, 0.1, 0.5, 1_000, FreshnessStatus.LIVE, Instant.now()
        );
    }

    private ShareSessionQuoteEntity entity(String symbol, Instant timestamp) {
        ShareSessionQuoteEntity entity = new ShareSessionQuoteEntity();
        entity.setSymbol(symbol);
        entity.setSessionType(MarketSession.POST_MARKET);
        entity.setExchange("NMS");
        entity.setExchangeTimezone("America/New_York");
        entity.setPrice(200);
        entity.setChangePercent(1);
        entity.setDeltaPercent(0.1);
        entity.setRollingPercent(0.5);
        entity.setVolume(1_000);
        entity.setProvider("YAHOO");
        entity.setProviderTimestamp(timestamp);
        entity.setFreshnessStatus(FreshnessStatus.LIVE);
        return entity;
    }
}
