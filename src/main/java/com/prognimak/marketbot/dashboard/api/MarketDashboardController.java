package com.prognimak.marketbot.dashboard.api;

import com.prognimak.marketbot.dashboard.config.MarketDashboardProperties;
import com.prognimak.marketbot.dashboard.model.MarketChartRange;
import com.prognimak.marketbot.dashboard.model.MarketChartResponse;
import com.prognimak.marketbot.dashboard.model.MarketDashboardSnapshot;
import com.prognimak.marketbot.dashboard.service.MarketChartService;
import com.prognimak.marketbot.dashboard.service.MarketDashboardService;
import com.prognimak.marketbot.service.MarketScannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("!history-backfill")
@RequestMapping("/api/dashboard")
@CrossOrigin(originPatterns = "*")
@RequiredArgsConstructor
public class MarketDashboardController {
    private final MarketDashboardProperties properties;
    private final MarketDashboardService dashboardService;
    private final MarketChartService chartService;
    private final MarketScannerService marketScannerService;

    @GetMapping("/snapshot")
    public MarketDashboardSnapshot snapshot() {
        return dashboardService.latestSnapshot();
    }

    @GetMapping("/chart/{symbol}")
    public MarketChartResponse chart(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "today") String range
    ) {
        try {
            return chartService.chart(symbol, MarketChartRange.parse(range));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported chart range: " + range, e);
        }
    }

    @PostMapping("/scan")
    public ResponseEntity<MarketDashboardSnapshot> scan() {
        if (!properties.manualScanEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manual scan is disabled");
        }

        marketScannerService.scanMarket();
        return ResponseEntity.accepted().body(dashboardService.latestSnapshot());
    }
}
