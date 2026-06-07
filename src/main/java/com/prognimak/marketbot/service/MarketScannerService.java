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
import com.prognimak.marketbot.notification.AsyncNotificationService;
import com.prognimak.marketbot.repository.QuoteRepository;
import com.prognimak.marketbot.entity.AppUserPropertyEntity;
import com.prognimak.marketbot.repository.UserSymbolAlertStateRepository;
import com.prognimak.marketbot.user.model.UserAlertSettings;
import com.prognimak.marketbot.user.service.UserPropertyService;
import com.prognimak.marketbot.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import static com.prognimak.marketbot.util.Utils.*;

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
    private final AsyncNotificationService notificationService;
    private final UserSymbolAlertStateRepository alertStateRepository;

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
    @Scheduled(fixedDelayString = "${market-bot.scanner.poll-interval-ms}")
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
                List<QuoteEntity> persistedChanges = persistedHistory(symbol).changes();
                if (persistedChanges.isEmpty()) {
                    log.info("{} ({}) initial value saved: {}%", companyName, symbol, String.format(Locale.ROOT, "%.2f", currentPercent));
                    QuoteEntity entity = quoteMapper.toEntity(quote);
                    entity.setDelta(0);
                    quoteRepository.save(entity);
                    recordDashboardResult(quote, watchlistItem, currentPercent, 0, 0, false, null, List.of());
                    continue;
                }

                List<QuoteEntity> lastPersistedChanges = compatibleHistory(persistedChanges, quote);
                if (lastPersistedChanges.isEmpty()) {
                    log.info("{} ({}) new day-change baseline saved: {}%", companyName, symbol, String.format(Locale.ROOT, "%.2f", currentPercent));
                    QuoteEntity entity = quoteMapper.toEntity(quote);
                    entity.setDelta(0);
                    quoteRepository.save(entity);
                    recordDashboardResult(quote, watchlistItem, currentPercent, 0, 0, false, null, List.of());
                    continue;
                }

                QuoteEntity latestPersistedChange = lastPersistedChanges.getFirst();
                double delta = Utils.roundDouble(quote.percentChange() - latestPersistedChange.getPercentChange(), 2);
                QuoteEntity quoteEntity = quoteMapper.toEntity(quote);
                quoteEntity.setDelta(delta);
                if (!hasQuoteChanged(quote, latestPersistedChange, properties.scanner().quoteChangeEpsilon())) {
                    recordDashboardResult(quote, watchlistItem, latestPersistedChange.getPercentChange(), 0,
                            0, false, null,  lastPersistedChanges);

                    if (Math.abs(Utils.roundDouble(delta, 2)) >= properties.scanner().maxChangesForPersist()) {
                        log.info("Data persisted for symbol {}, company {}, delta ({}%) ", symbol, companyName, delta);
                        quoteRepository.save(quoteEntity);
                    }
                    //log.info("Skipping unchanged quote for {}: price={} percent={}", symbol, quote.current(), quote.percentChange());
                    continue;
                }

                List<Quote> quotes = quoteMapper.toQuotes(lastPersistedChanges);
                Collections.reverse(quotes);
                quotes.add(quote);

                double rollingDeltaSum = Utils.roundDouble(calculateRollingChanges(quotes), 2);
                /// Persist new Quote
                QuoteEntity savedQuoteEntity = savedQuote(quoteEntity);

                boolean haveSendFlag = false;
                String messageText = null;
                List<AppUserPropertyEntity> usersWatchingSymbol = userPropertyService.findUsersWatchingSymbol(symbol);
                if (!usersWatchingSymbol.isEmpty()) {
                    log.info(
                            "{}{} ({}) {} current: {}% | previous: {}% | delta: {}% | rolling {}: {}%{}",
                            colorFor(rollingDeltaSum),
                            companyName,
                            symbol,
                            directionLabel(rollingDeltaSum),
                            formatPercent(currentPercent),
                            formatPercent(latestPersistedChange.getPercentChange()),
                            formatPercent(delta),
                            properties.alert().maximalRollingSize(),
                            formatPercent(rollingDeltaSum),
                            TEXT_COLOR_NON
                    );

                    for (AppUserPropertyEntity userWatchConfig : usersWatchingSymbol) {
                        Long userId = userWatchConfig.getUser().getId();
                        UserAlertSettings alertSettings = userPropertyService.loadSharesAlertSettings(userId);
                        double userRollingDelta = rollingDeltaForUser(userId, symbol, quote, lastPersistedChanges, rollingDeltaSum);

                        if (!shouldSendForUser(alertSettings, delta, userRollingDelta)) {
                            log.info("Share alert skipped for user {} and symbol {}: delta {}% / threshold {}%, rolling {}% / threshold {}%",
                                    userId,
                                    symbol,
                                    formatPercent(delta),
                                    formatPercent(alertSettings.deltaThreshold()),
                                    formatPercent(userRollingDelta),
                                    formatPercent(alertSettings.rollingThreshold()));
                            continue;
                        }
                        if (hasAlertAlreadyBeenSent(userId, symbol, savedQuoteEntity)) {
                            log.info("Share alert skipped for user {} and symbol {}: quote {} was already sent.",
                                    userId, symbol, savedQuoteEntity.getId());
                            continue;
                        }
                        String userMessageText = buildMessage(quote, watchlistItem, delta, userRollingDelta, lastPersistedChanges.size());
                        if (notificationService.sendShareAlert(userId, symbol, savedQuoteEntity.getId(), userMessageText)) {
                            haveSendFlag = true;
                            if (messageText == null) {
                                messageText = userMessageText;
                            }
                        }
                    }
                }
                recordDashboardResult(quote, watchlistItem, latestPersistedChange.getPercentChange(), delta, rollingDeltaSum, haveSendFlag, messageText, lastPersistedChanges);

               // Thread.sleep(500);
            } catch (Exception e) {
                log.warn("Error checking {} ({}): {}", companyName, symbol, e.getMessage(), e);
            }
        }
        marketDashboardService.publishSnapshot(scanStartedAt);
        log.info("End scanning market...");
    }

    private String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private PersistedHistory persistedHistory(String symbol) {
        List<QuoteEntity> recentChanges = quoteRepository.findBySymbolOrderByCreatedDesc(
                symbol, PageRequest.of(0, properties.alert().maximalRollingSize()));
        if (recentChanges == null) {
            return new PersistedHistory(List.of(), false);
        }

        return new PersistedHistory(recentChanges, false);
    }

    private record PersistedHistory(List<QuoteEntity> changes, boolean unsent) {
    }

    private List<QuoteEntity> compatibleHistory(List<QuoteEntity> recentChanges, Quote quote) {
        return recentChanges.stream()
                .takeWhile(persisted ->
                        sameQuoteBaseline(persisted, quote, properties.scanner().quoteChangeEpsilon()))
                .toList();
    }

    private QuoteEntity savedQuote(QuoteEntity quoteEntity) {
        QuoteEntity saved = quoteRepository.save(quoteEntity);
        return saved == null ? quoteEntity : saved;
    }

    private boolean hasAlertAlreadyBeenSent(Long userId, String symbol, QuoteEntity quoteEntity) {
        if (quoteEntity.getId() == null) {
            return false;
        }
        return alertStateRepository.findByUserIdAndSymbolIgnoreCase(userId, symbol)
                .map(state -> state.getLastSentQuote() != null
                        && Objects.equals(state.getLastSentQuote().getId(), quoteEntity.getId()))
                .orElse(false);
    }

    private double rollingDeltaForUser(
            Long userId,
            String symbol,
            Quote quote,
            List<QuoteEntity> lastPersistedChanges,
            double defaultRollingDelta
    ) {
        return alertStateRepository.findByUserIdAndSymbolIgnoreCase(userId, symbol)
                .map(state -> state.getLastSentQuote() == null ? null : state.getLastSentQuote())
                .filter(lastSentQuote -> isInsideRollingWindow(lastSentQuote, lastPersistedChanges))
                .map(lastSentQuote -> Utils.roundDouble(quote.percentChange() - lastSentQuote.getPercentChange(), 2))
                .orElse(defaultRollingDelta);
    }

    private boolean isInsideRollingWindow(QuoteEntity quote, List<QuoteEntity> lastPersistedChanges) {
        if (quote.getId() == null) {
            return false;
        }
        return lastPersistedChanges.stream()
                .map(QuoteEntity::getId)
                .filter(Objects::nonNull)
                .anyMatch(id -> Objects.equals(id, quote.getId()));
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
            double rollingDelta,
            int rollingWindowSize
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
            Move since last saved quote: %.2f%%
            Alert window move (%d saved quotes): %.2f%%
            
            Price: $%.2f
            Today range: $%.2f - $%.2f
            Open: $%.2f
            Previous close: $%.2f
            """.formatted(
                icon,
                timestamp,
                companyLabel,
                quote.symbol(),
                direction,
                quote.percentChange(),
                delta,
                rollingWindowSize,
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
        for (int attempt = 1; attempt <= properties.scanner().maxQuoteFetchAttempts(); attempt++) {
            try {
                return getQuoteForProvider(symbol);
            } catch (WebClientResponseException.NotFound e) {
                // TODO: Track repeated 404s and temporarily disable unavailable symbols instead of logging every scan.
                throw new IllegalArgumentException("Quote provider returned 404 for symbol " + symbol, e);
            } catch (WebClientResponseException e) {
                if (!isTransientProviderError(e) || attempt == properties.scanner().maxQuoteFetchAttempts()) {
                    throw new IllegalStateException("Quote provider error for symbol " + symbol
                            + ": HTTP " + e.getStatusCode().value(), e);
                }
                log.warn(
                        "Transient quote provider error for symbol {}: HTTP {}. Retry {}/{}.",
                        symbol,
                        e.getStatusCode().value(),
                        attempt,
                        properties.scanner().maxQuoteFetchAttempts()
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
            Thread.sleep(properties.scanner().quoteFetchRetryDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry quote for symbol " + symbol, e);
        }
    }

    private boolean isYahooEuropeSymbol(String symbol) {
        return YAHOO_EU_SUFFIXES.stream().anyMatch(symbol::endsWith);
    }
}
