package com.prognimak.marketbot.dashboard.api;

import com.prognimak.marketbot.dashboard.config.MarketDashboardProperties;
import com.prognimak.marketbot.dashboard.model.CryptoDashboardSnapshot;
import com.prognimak.marketbot.dashboard.model.MarketCandleResponse;
import com.prognimak.marketbot.dashboard.model.MarketChartRange;
import com.prognimak.marketbot.dashboard.model.MarketChartResponse;
import com.prognimak.marketbot.dashboard.service.CryptoChartService;
import com.prognimak.marketbot.dashboard.service.CryptoCandleService;
import com.prognimak.marketbot.dashboard.service.CryptoDashboardService;
import com.prognimak.marketbot.security.AppUserPrincipal;
import com.prognimak.marketbot.service.CryptoScannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/crypto-dashboard")
@CrossOrigin(originPatterns = "*")
@RequiredArgsConstructor
public class CryptoDashboardController {
    private final MarketDashboardProperties properties;
    private final CryptoDashboardService cryptoDashboardService;
    private final CryptoChartService cryptoChartService;
    private final CryptoCandleService cryptoCandleService;
    private final CryptoScannerService cryptoScannerService;

    @GetMapping("/snapshot")
    public CryptoDashboardSnapshot snapshot(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            return cryptoDashboardService.latestSnapshot();
        }
        return cryptoDashboardService.latestSnapshot(principal.user().getId());
    }

    @GetMapping("/candles/{symbol}")
    public MarketCandleResponse candles(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "today") String range
    ) {
        try {
            return cryptoCandleService.candles(symbol, MarketChartRange.parse(range));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported candle range: " + range, e);
        }
    }

    @GetMapping("/chart/{symbol}")
    public MarketChartResponse chart(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "today") String range
    ) {
        try {
            return cryptoChartService.chart(symbol, MarketChartRange.parse(range));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported chart range: " + range, e);
        }
    }

    @PostMapping("/scan")
    public ResponseEntity<CryptoDashboardSnapshot> scan(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (!properties.manualScanEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manual scan is disabled");
        }

        cryptoScannerService.scanCryptoMarket();
        if (principal == null) {
            return ResponseEntity.accepted().body(cryptoDashboardService.latestSnapshot());
        }
        return ResponseEntity.accepted().body(cryptoDashboardService.latestSnapshot(principal.user().getId()));
    }
}
