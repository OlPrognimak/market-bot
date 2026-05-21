package com.prognimak.marketbot.dashboard.api;

import com.prognimak.marketbot.dashboard.config.MarketDashboardProperties;
import com.prognimak.marketbot.dashboard.model.MarketDashboardSnapshot;
import com.prognimak.marketbot.dashboard.service.MarketDashboardService;
import com.prognimak.marketbot.service.MarketScannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(originPatterns = "*")
@RequiredArgsConstructor
public class MarketDashboardController {
    private final MarketDashboardProperties properties;
    private final MarketDashboardService dashboardService;
    private final MarketScannerService marketScannerService;

    @GetMapping("/snapshot")
    public MarketDashboardSnapshot snapshot() {
        return dashboardService.latestSnapshot();
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
