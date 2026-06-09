package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.dashboard.model.ExtendedHoursResult;
import com.prognimak.marketbot.model.FreshnessStatus;
import com.prognimak.marketbot.model.MarketSession;
import com.prognimak.marketbot.user.service.UserPropertyService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ExtendedHoursDashboardServiceTest {

    private final ExtendedHoursDashboardService service =
            new ExtendedHoursDashboardService(mock(UserPropertyService.class));

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

    private ExtendedHoursResult result(MarketSession session) {
        return new ExtendedHoursResult(
                "AAPL", "Apple", "US", "Technology", "NMS", session,
                200, 1, 0.1, 0.5, 1_000, FreshnessStatus.LIVE, Instant.now()
        );
    }
}
