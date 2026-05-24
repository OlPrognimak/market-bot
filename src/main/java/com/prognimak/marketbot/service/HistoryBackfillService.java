package com.prognimak.marketbot.service;

import com.prognimak.marketbot.client.YahooHistoricalDataClient;
import com.prognimak.marketbot.config.HistoryBackfillProperties;
import com.prognimak.marketbot.entity.HistoryQuoteEntity;
import com.prognimak.marketbot.model.HistoryIntervalType;
import com.prognimak.marketbot.model.WatchlistItem;
import com.prognimak.marketbot.repository.HistoryQuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryBackfillService {
    private final HistoryBackfillProperties properties;
    private final WatchlistService watchlistService;
    private final YahooHistoricalDataClient historicalDataClient;
    private final HistoryQuoteRepository historyQuoteRepository;

    @Transactional
    public ImportSummary backfill() {
        LocalDate from = properties.effectiveFrom();
        LocalDate to = properties.effectiveTo();
        String source = properties.effectiveSource();
        HistoryIntervalType intervalType = properties.effectiveIntervalType();
        Map<String, WatchlistItem> watchlist = selectedWatchlist();

        int imported = 0;
        int skipped = 0;
        int failed = 0;

        log.info("Starting history backfill: symbols={}, from={}, to={}, source={}, interval={}",
                watchlist.size(), from, to, source, intervalType);

        for (String symbol : watchlist.keySet()) {
            try {
                List<HistoryQuoteEntity> quotes = historicalDataClient.historicalQuotes(symbol, from, to, source, intervalType);
                int importedForSymbol = 0;
                int skippedForSymbol = 0;

                for (HistoryQuoteEntity quote : quotes) {
                    if (historyQuoteRepository.existsBySymbolAndTradingDateAndIntervalType(
                            quote.getSymbol(),
                            quote.getTradingDate(),
                            quote.getIntervalType()
                    )) {
                        skipped++;
                        skippedForSymbol++;
                        continue;
                    }

                    historyQuoteRepository.save(quote);
                    imported++;
                    importedForSymbol++;
                }

                log.info("Backfilled {}: imported={}, skipped={}", symbol, importedForSymbol, skippedForSymbol);
                delayBetweenRequests();
            } catch (Exception e) {
                failed++;
                log.error("Failed to backfill historical data for {}", symbol, e);
            }
        }

        ImportSummary summary = new ImportSummary(imported, skipped, failed);
        log.info("Finished history backfill: {}", summary);
        return summary;
    }

    private Map<String, WatchlistItem> selectedWatchlist() {
        Map<String, WatchlistItem> watchlist = new LinkedHashMap<>(watchlistService.watchlist());
        String symbols = properties.symbols();
        if (symbols == null || symbols.isBlank()) {
            return watchlist;
        }

        Map<String, WatchlistItem> selected = new LinkedHashMap<>();
        Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(symbol -> !symbol.isBlank())
                .forEach(symbol -> {
                    WatchlistItem item = watchlist.get(symbol);
                    selected.put(symbol, item == null ? WatchlistItem.simple(symbol, symbol) : item);
                });

        return selected;
    }

    private void delayBetweenRequests() throws InterruptedException {
        long requestDelayMs = properties.effectiveRequestDelayMs();
        if (requestDelayMs > 0) {
            Thread.sleep(requestDelayMs);
        }
    }

    public record ImportSummary(int imported, int skipped, int failed) {
    }
}
