package com.prognimak.marketbot.news.api;

import com.prognimak.marketbot.news.model.InstrumentType;
import com.prognimak.marketbot.news.model.NewsInsightResponse;
import com.prognimak.marketbot.news.model.NewsRefreshRequest;
import com.prognimak.marketbot.news.model.NewsRefreshResponse;
import com.prognimak.marketbot.news.model.NewsResearchLayer;
import com.prognimak.marketbot.news.model.NewsResearchRequest;
import com.prognimak.marketbot.news.model.NewsResearchResponse;
import com.prognimak.marketbot.news.service.NewsInsightQueryService;
import com.prognimak.marketbot.news.service.NewsMonitoringService;
import com.prognimak.marketbot.news.service.NewsResearchService;
import com.prognimak.marketbot.security.AppUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/news/insights")
@RequiredArgsConstructor
@Slf4j
public class NewsInsightController {
    private final NewsMonitoringService monitoringService;
    private final NewsInsightQueryService queryService;
    private final NewsResearchService researchService;

    @PostMapping("/refresh")
    public NewsRefreshResponse refresh(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody NewsRefreshRequest request
    ) {
        try {
            return monitoringService.startRefresh(principal.user().getId(), request.instrumentType(), request.symbol());
        } catch (RuntimeException exception) {
            log.error(
                    "News refresh request failed for user {}, instrument {}:{}: {}",
                    principal.user().getId(),
                    request.instrumentType(),
                    request.symbol(),
                    exception.getMessage(),
                    exception
            );
            throw exception;
        }
    }

    @GetMapping("/refresh-status")
    public NewsRefreshResponse refreshStatus(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam InstrumentType instrumentType,
            @RequestParam String symbol
    ) {
        try {
            return monitoringService.status(principal.user().getId(), instrumentType, symbol);
        } catch (RuntimeException exception) {
            log.error(
                    "News refresh status request failed for user {}, instrument {}:{}: {}",
                    principal.user().getId(),
                    instrumentType,
                    symbol,
                    exception.getMessage(),
                    exception
            );
            throw exception;
        }
    }

    @GetMapping
    public List<NewsInsightResponse> find(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam InstrumentType instrumentType,
            @RequestParam String symbol,
            @RequestParam(defaultValue = "20") int limit
    ) {
        try {
            return queryService.find(principal.user().getId(), instrumentType, symbol, limit);
        } catch (RuntimeException exception) {
            log.error(
                    "Saved news query failed for user {}, instrument {}:{}: {}",
                    principal.user().getId(),
                    instrumentType,
                    symbol,
                    exception.getMessage(),
                    exception
            );
            throw exception;
        }
    }

    @PostMapping("/research")
    public NewsResearchResponse research(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody NewsResearchRequest request
    ) {
        return researchService.start(principal.user().getId(), request.instrumentType(), request.symbol(), request.layer());
    }

    @GetMapping("/research-status")
    public NewsResearchResponse researchStatus(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam InstrumentType instrumentType,
            @RequestParam String symbol,
            @RequestParam NewsResearchLayer layer
    ) {
        return researchService.status(principal.user().getId(), instrumentType, symbol, layer);
    }
}
