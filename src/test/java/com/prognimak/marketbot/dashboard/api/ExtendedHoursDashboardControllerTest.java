package com.prognimak.marketbot.dashboard.api;

import com.prognimak.marketbot.dashboard.model.ExtendedHoursSnapshot;
import com.prognimak.marketbot.dashboard.service.ExtendedHoursDashboardService;
import com.prognimak.marketbot.model.MarketSession;
import com.prognimak.marketbot.service.ExtendedHoursScannerService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExtendedHoursDashboardControllerTest {

    @Test
    void scanRunsScannerBeforeReturningSnapshot() {
        ExtendedHoursDashboardService dashboardService = mock(ExtendedHoursDashboardService.class);
        ExtendedHoursScannerService scannerService = mock(ExtendedHoursScannerService.class);
        ExtendedHoursDashboardController controller = new ExtendedHoursDashboardController(dashboardService, scannerService);
        ExtendedHoursSnapshot snapshot = new ExtendedHoursSnapshot(
                Instant.parse("2026-07-17T12:00:00Z"),
                LocalDate.of(2026, 7, 17),
                LocalDate.of(2026, 7, 17),
                false,
                List.of()
        );
        when(dashboardService.snapshot(null, MarketSession.PRE_MARKET)).thenReturn(snapshot);

        ExtendedHoursSnapshot result = controller.scan(null, MarketSession.PRE_MARKET);

        assertSame(snapshot, result);
        InOrder inOrder = inOrder(scannerService, dashboardService);
        inOrder.verify(scannerService).scan();
        inOrder.verify(dashboardService).snapshot(null, MarketSession.PRE_MARKET);
    }
}
