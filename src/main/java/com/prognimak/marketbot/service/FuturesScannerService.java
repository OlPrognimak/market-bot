package com.prognimak.marketbot.service;

import com.prognimak.marketbot.client.YahooFinanceClient;
import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.dashboard.model.FuturesResult;
import com.prognimak.marketbot.dashboard.service.FuturesDashboardService;
import com.prognimak.marketbot.entity.FuturesCatalogEntity;
import com.prognimak.marketbot.entity.FuturesQuoteEntity;
import com.prognimak.marketbot.model.FreshnessStatus;
import com.prognimak.marketbot.model.YahooSessionQuote;
import com.prognimak.marketbot.repository.FuturesCatalogRepository;
import com.prognimak.marketbot.repository.FuturesQuoteRepository;
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

import static com.prognimak.marketbot.util.Utils.roundDouble;

@Service
@Profile("!history-backfill")
@RequiredArgsConstructor
@Slf4j
public class FuturesScannerService {
    private final AppProperties properties;
    private final YahooFinanceClient yahooFinanceClient;
    private final FuturesCatalogRepository catalogRepository;
    private final FuturesQuoteRepository quoteRepository;
    private final FuturesDashboardService dashboardService;

 //   @Scheduled(fixedDelayString = "${market-bot.futures.poll-interval-ms}")
    @Transactional
    public void scan() {
        if (!properties.futures().enabled()) return;
        Instant scanAt = Instant.now();
        for (FuturesCatalogEntity catalog : catalogRepository.findByEnabledTrueOrderBySymbolAsc()) {
            try {
                YahooSessionQuote quote = yahooFinanceClient.getSessionQuote(catalog.getProviderSymbol());
                FuturesQuoteEntity previous = quoteRepository.findFirstBySymbolOrderByProviderTimestampDesc(catalog.getSymbol()).orElse(null);
                if (previous != null && !quote.providerTimestamp().isAfter(previous.getProviderTimestamp())) {
                    dashboardService.record(toResult(previous, catalog));
                    continue;
                }
                List<FuturesQuoteEntity> history = quoteRepository.findBySymbolOrderByProviderTimestampDesc(
                        catalog.getSymbol(), PageRequest.of(0, properties.futures().rollingSize()));
                FuturesQuoteEntity entity = new FuturesQuoteEntity();
                entity.setSymbol(catalog.getSymbol());
                entity.setProviderSymbol(catalog.getProviderSymbol());
                entity.setPrice(quote.price());
                entity.setPriorSettlement(quote.baselinePrice());
                entity.setSessionOpen(quote.open());
                entity.setChangeFromSettlementPercent(percent(quote.price(), quote.baselinePrice()));
                entity.setChangeFromOpenPercent(percent(quote.price(), quote.open()));
                entity.setDeltaPercent(previous == null ? 0 : percent(quote.price(), previous.getPrice()));
                entity.setRollingPercent(history.isEmpty() ? 0 : percent(quote.price(), history.getLast().getPrice()));
                entity.setVolume(quote.volume());
                entity.setProvider("YAHOO");
                entity.setProviderTimestamp(quote.providerTimestamp());
                entity.setFreshnessStatus(freshness(quote.providerTimestamp()));
                FuturesQuoteEntity saved = quoteRepository.save(entity);
                dashboardService.record(toResult(saved, catalog));
            } catch (RuntimeException exception) {
                log.warn("Futures quote failed for {} ({}): {}", catalog.getSymbol(), catalog.getProviderSymbol(), exception.getMessage());
                log.debug("Futures quote failure for {}", catalog.getSymbol(), exception);
            }
        }
        dashboardService.publish(scanAt);
    }

    private FuturesResult toResult(FuturesQuoteEntity q, FuturesCatalogEntity c) {
        return new FuturesResult(c.getSymbol(), c.getName(), c.getUnderlying(), c.getRegion(), c.getExchange(), c.getCurrency(),
                q.getPrice(), q.getChangeFromSettlementPercent(), q.getChangeFromOpenPercent(), q.getDeltaPercent(),
                q.getRollingPercent(), q.getVolume(), q.getFreshnessStatus(), q.getProviderTimestamp());
    }

    private double percent(double current, double baseline) {
        return baseline <= 0 ? 0 : roundDouble(((current - baseline) / baseline) * 100, 2);
    }

    private FreshnessStatus freshness(Instant timestamp) {
        long delay = Math.max(0, Duration.between(timestamp, Instant.now()).toSeconds());
        if (delay > properties.futures().staleAfterSeconds()) return FreshnessStatus.STALE;
        if (delay > 30) return FreshnessStatus.DELAYED;
        return FreshnessStatus.LIVE;
    }
}
