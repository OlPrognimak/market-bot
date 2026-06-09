package com.prognimak.marketbot.dashboard.api;

import com.prognimak.marketbot.dashboard.model.FuturesSnapshot;
import com.prognimak.marketbot.dashboard.service.FuturesDashboardService;
import com.prognimak.marketbot.service.FuturesScannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!history-backfill")
@RequestMapping("/api/futures-dashboard")
@RequiredArgsConstructor
public class FuturesDashboardController {
    private final FuturesDashboardService dashboardService;
    private final FuturesScannerService scannerService;

    @GetMapping("/snapshot")
    public FuturesSnapshot snapshot() {
        return dashboardService.snapshot();
    }

    @PostMapping("/scan")
    public FuturesSnapshot scan() {
        scannerService.scan();
        return dashboardService.snapshot();
    }
}
