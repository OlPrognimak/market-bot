package com.prognimak.marketbot.service;

import com.prognimak.marketbot.client.YahooFinanceClient;
import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.dashboard.model.ExtendedHoursResult;
import com.prognimak.marketbot.dashboard.service.ExtendedHoursDashboardService;
import com.prognimak.marketbot.entity.ShareSessionQuoteEntity;
import com.prognimak.marketbot.model.*;
import com.prognimak.marketbot.repository.ShareSessionQuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.prognimak.marketbot.util.Utils.roundDouble;

@Service
@Profile("!history-backfill")
@RequiredArgsConstructor
@Slf4j
public class ExtendedHoursScannerService {
    private final AppProperties properties;
    private final WatchlistService watchlistService;
    private final YahooFinanceClient yahooFinanceClient;
    private final ShareSessionQuoteRepository repository;
    private final ExtendedHoursDashboardService dashboardService;

    @Scheduled(fixedDelayString = "${market-bot.extended-hours.poll-interval-ms}")
    @Transactional
    public void scan() {
        if (!properties.extendedHours().enabled()) {
            return;
        }
        Instant scanAt = Instant.now();
        for (Map.Entry<String, WatchlistItem> entry : watchlistService.watchlist().entrySet()) {
            if (!supportsExtendedHours(entry.getValue())) {
                dashboardService.retainCurrentSession(entry.getKey(), MarketSession.REGULAR);
                continue;
            }
            try {
                YahooSessionQuote quote = yahooFinanceClient.getSessionQuote(entry.getKey());
                dashboardService.retainCurrentSession(quote.symbol(), quote.activeSession());
                if (quote.activeSession() != MarketSession.PRE_MARKET && quote.activeSession() != MarketSession.POST_MARKET) {
                    continue;
                }
                if (quote.session() != quote.activeSession()) {
                    continue;
                }
                ShareSessionQuoteEntity previous = repository
                        .findFirstBySymbolAndSessionTypeOrderByProviderTimestampDesc(quote.symbol(), quote.session())
                        .orElse(null);
                if (previous != null && !quote.providerTimestamp().isAfter(previous.getProviderTimestamp())) {
                    dashboardService.record(toResult(previous, entry.getValue()));
                    continue;
                }
                List<ShareSessionQuoteEntity> history = repository.findBySymbolAndSessionTypeOrderByProviderTimestampDesc(
                        quote.symbol(), quote.session(), PageRequest.of(0, properties.extendedHours().rollingSize())
                );
                ShareSessionQuoteEntity entity = new ShareSessionQuoteEntity();
                entity.setSymbol(quote.symbol());
                entity.setSessionType(quote.session());
                entity.setExchange(quote.exchange());
                entity.setExchangeTimezone(quote.exchangeTimezone());
                entity.setPrice(quote.price());
                entity.setBaselinePrice(quote.baselinePrice());
                entity.setChangePercent(quote.changePercent());
                entity.setDeltaPercent(previous == null ? 0 : percent(quote.price(), previous.getPrice()));
                entity.setRollingPercent(history.isEmpty() ? 0 : percent(quote.price(), history.getLast().getPrice()));
                entity.setOpen(quote.open());
                entity.setHigh(quote.high());
                entity.setLow(quote.low());
                entity.setVolume(quote.volume());
                entity.setProvider("YAHOO");
                entity.setProviderTimestamp(quote.providerTimestamp());
                entity.setFreshnessStatus(freshness(quote.providerTimestamp(), properties.extendedHours().staleAfterSeconds()));
                ShareSessionQuoteEntity saved = repository.save(entity);
                dashboardService.record(toResult(saved, entry.getValue()));
            } catch (RuntimeException exception) {
                log.warn("Extended-hours quote failed for {}: {}", entry.getKey(), exception.getMessage());
                log.debug("Extended-hours quote failure for {}", entry.getKey(), exception);
            }
        }
        dashboardService.publish(scanAt);
    }

    private boolean supportsExtendedHours(WatchlistItem item) {
        if (item.region() != null && !item.region().isBlank()) {
            return "US".equalsIgnoreCase(item.region());
        }
        return !item.symbol().contains(".");
    }

    private ExtendedHoursResult toResult(ShareSessionQuoteEntity entity, WatchlistItem item) {
        return new ExtendedHoursResult(entity.getSymbol(), item.name(), item.region(), item.sector(), entity.getExchange(),
                entity.getSessionType(), entity.getPrice(), entity.getChangePercent(), entity.getDeltaPercent(),
                entity.getRollingPercent(), entity.getVolume(), entity.getFreshnessStatus(), entity.getProviderTimestamp());
    }

    private double percent(double current, double baseline) {
        return baseline <= 0 ? 0 : roundDouble(((current - baseline) / baseline) * 100, 2);
    }

    private FreshnessStatus freshness(Instant timestamp, long staleAfterSeconds) {
        long delay = Math.max(0, Duration.between(timestamp, Instant.now()).toSeconds());
        if (delay > staleAfterSeconds) return FreshnessStatus.STALE;
        if (delay > 30) return FreshnessStatus.DELAYED;
        return FreshnessStatus.LIVE;
    }
}
