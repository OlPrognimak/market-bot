package com.prognimak.marketbot.service;

import com.prognimak.marketbot.client.FinnhubClient;
import com.prognimak.marketbot.client.TelegramClient;
import com.prognimak.marketbot.client.YahooFinanceClient;
import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.dashboard.model.MarketDirection;
import com.prognimak.marketbot.dashboard.model.MarketScanResult;
import com.prognimak.marketbot.dashboard.service.MarketDashboardService;
import com.prognimak.marketbot.entity.QuoteEntity;
import com.prognimak.marketbot.mapper.QuoteMapper;
import com.prognimak.marketbot.model.Quote;
import com.prognimak.marketbot.model.WatchlistItem;
import com.prognimak.marketbot.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.prognimak.marketbot.util.Utils.calculateRollingChanges;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class MarketScannerService {
    private final QuoteRepository quoteRepository;
    private final FinnhubClient finnhubClient;
    private final YahooFinanceClient yahooFinanceClient;
    private final TelegramClient telegramClient;
    private final AppProperties properties;
    private final QuoteMapper quoteMapper;
    private final MarketDashboardService marketDashboardService;
    private final WatchlistService watchlistService;

    private static final String TEXT_COLOR_RED = "\u001B[31m";
    private static final String TEXT_COLOR_BLUE = "\u001B[34m";
    private static final String TEXT_COLOR_NON = "\u001B[0m";

    private static final int MAX_ROLLING_SIZE = 5;

private static final Set<String> YAHOO_EU_SUFFIXES = Set.of(
            ".AS", ".AT", ".BE", ".BR", ".CO", ".DE", ".DU", ".F",
            ".HE", ".HM", ".IC", ".IR", ".L", ".LS", ".MC", ".MI",
            ".MU", ".OL", ".PA", ".PR", ".SG", ".ST", ".SW", ".VI",
            ".WA"
    );

    /*That uses in case of using two providers.*/
    @SneakyThrows
    private  void getQuote(String symbol, AtomicReference<Quote>  atomicQuote) {
        try {
            Quote  quote = getQuoteForProvider(symbol);
            atomicQuote.set(quote);
        } catch (WebClientResponseException.NotFound e) {
            // TODO: Track repeated 404s and temporarily disable unavailable symbols instead of logging every scan.
            throw new IllegalArgumentException("Quote provider returned 404 for symbol " + symbol, e);
        }catch(Exception e) {
            //if (e.getMessage().contains("529")) {
                log.error("Error getting quote for symbol {}", symbol, e);
                Thread.currentThread().sleep(10000);
                getQuote(symbol, atomicQuote);
            //}
        }
    }

    /**
     * Scheduler scan shares markets every {@code fixedDelayString} and send message to user
     */
    @Scheduled(fixedDelayString = "${market-bot.poll-interval-ms}")
    public void scanMarket() {
        log.info("Start scanning market...");
        Instant scanStartedAt = Instant.now();
        Map<String, WatchlistItem> watchlist = watchlistService.watchlist();
        if (watchlist.isEmpty()) {
            log.warn("No symbols configured for market scan.");
            marketDashboardService.publishSnapshot(scanStartedAt);
            return;
        }

        for (Map.Entry<String, WatchlistItem> entry : watchlist.entrySet()) {

            String symbol = entry.getKey();
            WatchlistItem watchlistItem = entry.getValue();
            String companyName = watchlistItem.name();

            try {
                AtomicReference<Quote>  atomicQuote = new AtomicReference<>();
                getQuote(symbol, atomicQuote);
                Quote quote = atomicQuote.get();

                double currentPercent = quote.percentChange();
                PersistedHistory persistedHistory = persistedHistory(symbol);
                List<QuoteEntity> lastPersistedChanges = persistedHistory.changes();

                if (lastPersistedChanges.isEmpty()) {
                    System.out.printf("%s (%s) initial value saved: %.2f%%%n", companyName, symbol, currentPercent);
                    QuoteEntity entity = quoteMapper.toEntity(quote);
                    entity.setDelta(quote.percentChange());
                    quoteRepository.save(entity);
                    recordDashboardResult(quote, watchlistItem, currentPercent, 0, 0, false, null);
                    continue;
                }

                QuoteEntity latestPersistedChange = lastPersistedChanges.getFirst();

                List<Quote> quotes = quoteMapper.toQuotes(lastPersistedChanges);
                Collections.reverse(quotes);
                quotes.add(quote);

                double rollingDeltaSum = calculateRollingChanges(quotes);


                String color = rollingDeltaSum < 0?TEXT_COLOR_RED :TEXT_COLOR_NON;
                if(!color.equals(TEXT_COLOR_RED)) {
                    color = rollingDeltaSum > 0 ? TEXT_COLOR_BLUE : TEXT_COLOR_NON;
                }
                boolean exceedsMovementThreshold = Math.abs(rollingDeltaSum) >= properties.maximalDeltaPrice();


                QuoteEntity quoteEntity = quoteMapper.toEntity(quote);
                double delta = quote.percentChange() - latestPersistedChange.getPercentChange();
                quoteEntity.setDelta(delta);
                quoteRepository.save(quoteEntity);

                boolean haveSendFlag = false;
                String messageText = null;
                if (exceedsMovementThreshold /*exceedsMovementThreshold && exceedsAbsoluteThreshold*/) {
                    System.out.printf(
                            color +
                                    "%s (%s) current: %.2f%% | previous: %.2f%% | delta: %.2f%% | rolling 5: %.2f%%"
                                    + TEXT_COLOR_NON
                                    + "%n",
                            companyName,
                            symbol,
                            currentPercent,
                            latestPersistedChange.getPercentChange(),
                            delta,
                            rollingDeltaSum
                    );

                    messageText = buildMessage(quote, watchlistItem, delta, rollingDeltaSum);
                    telegramClient.sendMessage(messageText);
                    quoteEntity.setSend(true);
                    lastPersistedChanges.forEach(q -> q.setSend(true));
                    haveSendFlag = true;
                }
                recordDashboardResult(quote, watchlistItem, latestPersistedChange.getPercentChange(), delta, rollingDeltaSum, haveSendFlag, messageText);
                if(!haveSendFlag && persistedHistory.unsent() && lastPersistedChanges.size() >=  MAX_ROLLING_SIZE) {
                    //Can be set to true afater send message
                    lastPersistedChanges.stream().filter(
                            q ->q.isSend()==false).forEach(q -> q.setSend(true));
                }

               // Thread.sleep(500);
            } catch (Exception e) {
                System.err.println("Error checking " + companyName + " (" + symbol + "): " + e.getMessage());
            }
        }
        marketDashboardService.publishSnapshot(scanStartedAt);
        log.info("End scanning market...");
    }

    private PersistedHistory persistedHistory(String symbol) {
        List<QuoteEntity> unsentChanges = quoteRepository.findBySymbolAndSendIsFalseOrderByIdDesc(
                symbol, PageRequest.of(0, MAX_ROLLING_SIZE));
        if (unsentChanges != null && !unsentChanges.isEmpty()) {
            return new PersistedHistory(unsentChanges, true);
        }

        List<QuoteEntity> recentChanges = quoteRepository.findBySymbolOrderByIdDesc(
                symbol, PageRequest.of(0, MAX_ROLLING_SIZE));
        if (recentChanges == null) {
            return new PersistedHistory(List.of(), false);
        }

        return new PersistedHistory(recentChanges, false);
    }

    private record PersistedHistory(List<QuoteEntity> changes, boolean unsent) {
    }

    private void recordDashboardResult(
            Quote quote,
            WatchlistItem watchlistItem,
            double previousPercent,
            double delta,
            double rollingDelta,
            boolean alert,
            String messageText
    ) {
        marketDashboardService.recordResult(new MarketScanResult(
                quote.symbol(),
                watchlistItem.name(),
                watchlistItem.region(),
                watchlistItem.sector(),
                watchlistItem.exchange(),
                watchlistItem.currency(),
                watchlistItem.priority().name(),
                quote.percentChange(),
                previousPercent,
                delta,
                rollingDelta,
                MAX_ROLLING_SIZE,
                quote.current(),
                quote.low(),
                quote.high(),
                quote.open(),
                quote.previousClose(),
                direction(rollingDelta, delta),
                alert,
                Instant.now(),
                messageText
        ));
    }

    private MarketDirection direction(double rollingDelta, double delta) {
        double movement = Math.abs(rollingDelta) >= Math.abs(delta) ? rollingDelta : delta;
        if (movement > 0) {
            return MarketDirection.UP;
        }
        if (movement < 0) {
            return MarketDirection.DOWN;
        }
        return MarketDirection.NEUTRAL;
    }


    private String buildMessage(
            Quote quote,
            WatchlistItem watchlistItem,
            double delta,
            double rollingDelta
    ) {
        String icon = rollingDelta >= 0 ? "🟢" : "🔴";
        String direction = rollingDelta >= 0 ? "UP" : "DOWN";
        String companyLabel = companyLabel(watchlistItem);

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        return """
            %s %s
            
            %s (%s) %s
            
            Day change: %.2f%%
            Move since last check: %.2f%%
            Rolling move 5 checks: %.2f%%
            
            Price: $%.2f
            Today range: $%.2f - $%.2f
            Open: $%.2f
            Previous close: $%.2f
            """.formatted(
                timestamp,
                icon,
                companyLabel,
                quote.symbol(),
                direction,
                quote.percentChange(),
                delta,
                rollingDelta,
                quote.current(),
                quote.low(),
                quote.high(),
                quote.open(),
                quote.previousClose()
        );
    }

    private String companyLabel(WatchlistItem watchlistItem) {
        if (watchlistItem.region() == null || watchlistItem.region().isBlank()) {
            return watchlistItem.name();
        }

        return "%s (%s)".formatted(watchlistItem.name(), watchlistItem.region());
    }

    private Quote getQuoteForProvider(String symbol) {
        if (isYahooEuropeSymbol(symbol)) {
            return yahooFinanceClient.getQuote(symbol);
        }

        return yahooFinanceClient.getQuote(symbol);
    }

    private boolean isYahooEuropeSymbol(String symbol) {
        return YAHOO_EU_SUFFIXES.stream().anyMatch(symbol::endsWith);
    }
}
