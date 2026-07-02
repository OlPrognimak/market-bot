package com.prognimak.marketbot.dashboard.api;

import com.prognimak.marketbot.dashboard.model.ExtendedHoursSnapshot;
import com.prognimak.marketbot.dashboard.service.ExtendedHoursDashboardService;
import com.prognimak.marketbot.model.MarketSession;
import com.prognimak.marketbot.security.AppUserPrincipal;
import com.prognimak.marketbot.service.ExtendedHoursScannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!history-backfill")
@RequestMapping("/api/extended-hours-dashboard")
@RequiredArgsConstructor
public class ExtendedHoursDashboardController {
    private final ExtendedHoursDashboardService dashboardService;
    private final ExtendedHoursScannerService scannerService;

    @GetMapping("/snapshot")
    public ExtendedHoursSnapshot snapshot(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) MarketSession session
    ) {
        return dashboardService.snapshot(principal == null ? null : principal.user().getId(), session);
    }

    @PostMapping("/scan")
    public ExtendedHoursSnapshot scan(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) MarketSession session
    ) {
        //scannerService.scan();
        return dashboardService.snapshot(principal == null ? null : principal.user().getId(), session);
    }
}
