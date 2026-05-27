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
import com.prognimak.marketbot.notification.NotificationRouter;
import com.prognimak.marketbot.repository.QuoteRepository;
import com.prognimak.marketbot.entity.AppUserPropertyEntity;
import com.prognimak.marketbot.user.model.UserAlertSettings;
import com.prognimak.marketbot.user.service.UserPropertyService;
import com.prognimak.marketbot.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.prognimak.marketbot.util.Utils.calculateRollingChanges;

@Service
@Profile("!history-backfill")
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
    private final UserPropertyService userPropertyService;
    private final NotificationRouter notificationRouter;

    private static final String TEXT_COLOR_RED = "\u001B[31m";
    private static final String TEXT_COLOR_BLUE = "\u001B[34m";
    private static final String TEXT_COLOR_NON = "\u001B[0m";

    //private static final int MAX_ROLLING_SIZE = 5;

    private static final Set<String> YAHOO_EU_SUFFIXES = Set.of(
            ".AS", ".AT", ".BE", ".BR", ".CO", ".DE", ".DU", ".F",
            ".HE", ".HM", ".IC", ".IR", ".L", ".LS", ".MC", ".MI",
            ".MU", ".OL", ".PA", ".PR", ".SG", ".ST", ".SW", ".VI",
            ".WA"
    );

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
                Quote quote = getQuote(symbol);

                double currentPercent = quote.percentChange();
                PersistedHistory persistedHistory = persistedHistory(symbol);
                List<QuoteEntity> lastPersistedChanges = persistedHistory.changes();

                if (lastPersistedChanges.isEmpty()) {
                    System.out.printf("%s (%s) initial value saved: %.2f%%%n", companyName, symbol, currentPercent);
                    QuoteEntity entity = quoteMapper.toEntity(quote);
                    entity.setDelta(0);
                    quoteRepository.save(entity);
                    recordDashboardResult(quote, watchlistItem, currentPercent, 0, 0, false, null, lastPersistedChanges);
                    continue;
                }

                QuoteEntity latestPersistedChange = quoteRepository.findFirstBySymbolOrderByCreatedDesc(symbol)
                        .orElse(lastPersistedChanges.getFirst());
                double delta = quote.percentChange() - latestPersistedChange.getPercentChange();
                QuoteEntity quoteEntity = quoteMapper.toEntity(quote);
                quoteEntity.setDelta(delta);
                if (!hasQuoteChanged(quote, latestPersistedChange)) {
                    recordDashboardResult(quote, watchlistItem, latestPersistedChange.getPercentChange(), 0,
                            0, false, null,  lastPersistedChanges);

                    if (Math.abs(Utils.roundDouble(delta, 2)) >= properties.maxChangesForPersist()) {
                        quoteRepository.save(quoteEntity);
                    }
                    //log.info("Skipping unchanged quote for {}: price={} percent={}", symbol, quote.current(), quote.percentChange());
                    continue;
                }

                List<Quote> quotes = quoteMapper.toQuotes(lastPersistedChanges);
                Collections.reverse(quotes);
                quotes.add(quote);

                double rollingDeltaSum = calculateRollingChanges(quotes);


                String color = defineColor(rollingDeltaSum);
                /// Persist new Quote
                quoteRepository.save(quoteEntity);

                boolean haveSendFlag = false;
                String messageText = null;
                List<AppUserPropertyEntity> usersWatchingSymbol = userPropertyService.findUsersWatchingSymbol(symbol);
                if (!usersWatchingSymbol.isEmpty()) {
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
                    for (AppUserPropertyEntity userWatchConfig : usersWatchingSymbol) {
                        Long userId = userWatchConfig.getUser().getId();
                        if (shouldSendForUser(userId, delta, rollingDeltaSum)
                                && notificationRouter.send(userId, messageText)) {
                            haveSendFlag = true;
                        }
                    }
                    if (haveSendFlag) {
                        quoteEntity.setSend(true);
                        lastPersistedChanges.forEach(q -> q.setSend(true));
                    }
                }
                recordDashboardResult(quote, watchlistItem, latestPersistedChange.getPercentChange(), delta, rollingDeltaSum, haveSendFlag, messageText, lastPersistedChanges);
                if(!haveSendFlag && persistedHistory.unsent() && lastPersistedChanges.size() >=  properties.maximalRollingSize()) {
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

    private boolean shouldSendForUser(Long userId, double delta, double rollingDeltaSum) {
        UserAlertSettings settings = userPropertyService.loadAlertSettings(userId);
        return Math.abs(delta) >= settings.deltaThreshold()
                && Math.abs(Utils.roundDouble(rollingDeltaSum, 2)) >= settings.rollingThreshold();
    }

    private static @NonNull String defineColor(double rollingDeltaSum) {
        String color = rollingDeltaSum < 0?TEXT_COLOR_RED :TEXT_COLOR_NON;
        if(!color.equals(TEXT_COLOR_RED)) {
            color = rollingDeltaSum > 0 ? TEXT_COLOR_BLUE : TEXT_COLOR_NON;
        }
        return color;
    }

    private PersistedHistory persistedHistory(String symbol) {
        List<QuoteEntity> unsentChanges = quoteRepository.findBySymbolAndSendIsFalseOrderByCreatedDesc(
                symbol, PageRequest.of(0, properties.maximalRollingSize()));
        if (unsentChanges != null && !unsentChanges.isEmpty()) {
            return new PersistedHistory(unsentChanges, true);
        }

        List<QuoteEntity> recentChanges = quoteRepository.findBySymbolOrderByCreatedDesc(
                symbol, PageRequest.of(0, properties.maximalRollingSize()));
        if (recentChanges == null) {
            return new PersistedHistory(List.of(), false);
        }

        return new PersistedHistory(recentChanges, false);
    }

    private record PersistedHistory(List<QuoteEntity> changes, boolean unsent) {
    }

    private boolean hasQuoteChanged(Quote quote, QuoteEntity latestPersistedChange) {
        return changed(quote.current(), latestPersistedChange.getCurrent())
                || changed(quote.percentChange(), latestPersistedChange.getPercentChange())
                || changed(quote.change(), latestPersistedChange.getChange())
                || changed(quote.high(), latestPersistedChange.getHigh())
                || changed(quote.low(), latestPersistedChange.getLow())
                || changed(quote.open(), latestPersistedChange.getOpen())
                || changed(quote.previousClose(), latestPersistedChange.getPreviousClose());
    }

    private boolean changed(double left, double right) {
        return Math.abs(left - right) >= properties.quoteChangeEpsilon();
    }

    private void recordDashboardResult(
            Quote quote,
            WatchlistItem watchlistItem,
            double previousPercent,
            double delta,
            double rollingDelta,
            boolean alert,
            String messageText,
            List<QuoteEntity> changes
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
                changes.size(),
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
        return yahooFinanceClient.getQuote(symbol);
    }

    /*That uses in case of using two providers.*/
    private Quote getQuote(String symbol) {
        for (int attempt = 1; attempt <= properties.maxQuoteFetchAttempts(); attempt++) {
            try {
                return getQuoteForProvider(symbol);
            } catch (WebClientResponseException.NotFound e) {
                // TODO: Track repeated 404s and temporarily disable unavailable symbols instead of logging every scan.
                throw new IllegalArgumentException("Quote provider returned 404 for symbol " + symbol, e);
            } catch (WebClientResponseException e) {
                if (!isTransientProviderError(e) || attempt == properties.maxQuoteFetchAttempts()) {
                    throw new IllegalStateException("Quote provider error for symbol " + symbol
                            + ": HTTP " + e.getStatusCode().value(), e);
                }
                log.warn(
                        "Transient quote provider error for symbol {}: HTTP {}. Retry {}/{}.",
                        symbol,
                        e.getStatusCode().value(),
                        attempt,
                        properties.maxQuoteFetchAttempts()
                );
                sleepBeforeRetry(symbol);
            } catch (Exception e) {
                throw new IllegalStateException("Could not load quote for symbol " + symbol + ": " + e.getMessage(), e);
            }
        }

        throw new IllegalStateException("Could not load quote for symbol " + symbol);
    }

    private boolean isTransientProviderError(WebClientResponseException e) {
        int statusCode = e.getStatusCode().value();
        return statusCode == 429 || e.getStatusCode().is5xxServerError();
    }

    private void sleepBeforeRetry(String symbol) {
        try {
            Thread.sleep(properties.quoteFetchRetryDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry quote for symbol " + symbol, e);
        }
    }

    private boolean isYahooEuropeSymbol(String symbol) {
        return YAHOO_EU_SUFFIXES.stream().anyMatch(symbol::endsWith);
    }
}
