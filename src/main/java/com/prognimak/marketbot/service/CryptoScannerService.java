package com.prognimak.marketbot.service;

import com.prognimak.marketbot.client.BinanceClient;
import com.prognimak.marketbot.client.CoinGeckoClient;
import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.dashboard.model.CryptoScanResult;
import com.prognimak.marketbot.dashboard.model.MarketDirection;
import com.prognimak.marketbot.dashboard.service.CryptoDashboardService;
import com.prognimak.marketbot.entity.AppUserPropertyEntity;
import com.prognimak.marketbot.entity.CryptoQuoteEntity;
import com.prognimak.marketbot.model.BinanceExchangeInfoResponse;
import com.prognimak.marketbot.model.BinanceTickerResponse;
import com.prognimak.marketbot.model.CoinGeckoSearchResponse;
import com.prognimak.marketbot.model.CryptoMovement;
import com.prognimak.marketbot.model.CryptoWatchlistItem;
import com.prognimak.marketbot.notification.AsyncNotificationService;
import com.prognimak.marketbot.repository.CryptoQuoteRepository;
import com.prognimak.marketbot.repository.CryptoUserSymbolAlertStateRepository;
import com.prognimak.marketbot.user.model.UserAlertSettings;
import com.prognimak.marketbot.user.service.UserPropertyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.prognimak.marketbot.util.Utils.roundDouble;
import static com.prognimak.marketbot.util.Utils.shouldSendForUser;

@Slf4j
@Service
@Profile("!history-backfill")
@RequiredArgsConstructor
@Transactional
public class CryptoScannerService {
    private static final String QUOTE_ASSET = "USDT";
    private static final Set<String> LEVERAGED_SUFFIXES = Set.of("UP", "DOWN", "BULL", "BEAR");

    private final AppProperties properties;
    private final BinanceClient binanceClient;
    private final CoinGeckoClient coinGeckoClient;
    private final CryptoWatchlistService cryptoWatchlistService;
    private final UserPropertyService userPropertyService;
    private final AsyncNotificationService notificationService;
    private final CryptoDashboardService cryptoDashboardService;
    private final CryptoQuoteRepository cryptoQuoteRepository;
    private final CryptoUserSymbolAlertStateRepository cryptoAlertStateRepository;

    @Scheduled(fixedDelayString = "${market-bot.crypto.poll-interval-ms}")
    public void scanCryptoMarket() {
        if (!properties.crypto().scannerEnabled()) {
            return;
        }

        log.info("Start scanning crypto market...");
        Instant scanStartedAt = Instant.now();
        Map<String, CryptoWatchlistItem> watchlist = cryptoWatchlistService.watchlist();
        if (watchlist.isEmpty()) {
            log.warn("No crypto coins configured for market ƒscan.");
            cryptoDashboardService.publishSnapshot(scanStartedAt);
            return;
        }

        try {
            Map<String, BinanceExchangeInfoResponse.SymbolInfo> activePairs = activeUsdtPairs(watchlist);
            Map<String, Double> quoteVolumeBySymbol = quoteVolumeBySymbol(activePairs);
            Map<String, String> coinNameBySymbol = coinNameBySymbol(watchlist);

            watchlist.values().stream()
                    .map(item -> movement(item, activePairs, quoteVolumeBySymbol, coinNameBySymbol))
                    .flatMap(Optional::stream)
                    .forEach(this::recordMovement);
        } catch (Exception e) {
            log.warn("Crypto scan cycle failed: {}", e.getMessage(), e);
        } finally {
            cryptoDashboardService.publishSnapshot(scanStartedAt);
            log.info("End scanning crypto market.");
        }
    }

    private Map<String, BinanceExchangeInfoResponse.SymbolInfo> activeUsdtPairs(Map<String, CryptoWatchlistItem> watchlist) {
        return watchlist.values().stream()
                .map(this::activeUsdtPair)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(BinanceExchangeInfoResponse.SymbolInfo::baseAsset, pair -> pair, (left, right) -> left));
    }

    private Optional<BinanceExchangeInfoResponse.SymbolInfo> activeUsdtPair(CryptoWatchlistItem item) {
        String pairSymbol = item.symbol() + QUOTE_ASSET;
        try {
            BinanceExchangeInfoResponse response = binanceClient.exchangeInfo(pairSymbol);
            if (response == null || response.symbols() == null || response.symbols().isEmpty()) {
                return Optional.empty();
            }

            return response.symbols().stream()
                    .filter(symbol -> "TRADING".equals(symbol.status()))
                    .filter(symbol -> QUOTE_ASSET.equals(symbol.quoteAsset()))
                    .filter(symbol -> pairSymbol.equals(symbol.symbol()))
                    .filter(symbol -> symbol.isSpotTradingAllowed() == null || symbol.isSpotTradingAllowed())
                    .filter(symbol -> !isLeveragedToken(symbol.baseAsset()))
                    .findFirst();
        } catch (Exception e) {
            log.warn("Skipping crypto {} because Binance exchange info failed: {}", pairSymbol, e.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Double> quoteVolumeBySymbol(Map<String, BinanceExchangeInfoResponse.SymbolInfo> activePairs) {
        return activePairs.values().stream()
                .map(pair -> ticker(pair.symbol()))
                .flatMap(Optional::stream)
                .filter(ticker -> ticker.symbol() != null && ticker.quoteVolume() != null)
                .collect(Collectors.toMap(
                        BinanceTickerResponse::symbol,
                        ticker -> parseDouble(ticker.quoteVolume()),
                        (left, right) -> left
                ));
    }

    private Optional<BinanceTickerResponse> ticker(String symbol) {
        try {
            return Optional.ofNullable(binanceClient.ticker24h(symbol));
        } catch (Exception e) {
            log.warn("Could not load Binance ticker for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, String> coinNameBySymbol(Map<String, CryptoWatchlistItem> watchlist) {
        return watchlist.values().stream()
                .collect(Collectors.toMap(
                        CryptoWatchlistItem::symbol,
                        item -> coinName(item).orElse(item.name()),
                        (left, right) -> left
                ));
    }

    private Optional<String> coinName(CryptoWatchlistItem item) {
        try {
            CoinGeckoSearchResponse response = coinGeckoClient.search(item.symbol());
            if (response == null || response.coins() == null) {
                return Optional.empty();
            }
            return response.coins().stream()
                    .filter(coin -> coin.symbol() != null && coin.name() != null)
                    .filter(coin -> item.symbol().equals(coin.symbol().toUpperCase(Locale.ROOT)))
                    .map(CoinGeckoSearchResponse.Coin::name)
                    .findFirst();
        } catch (Exception e) {
            log.warn("Could not load CoinGecko metadata for {}. Falling back to configured name: {}", item.symbol(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<CryptoMovement> movement(
            CryptoWatchlistItem item,
            Map<String, BinanceExchangeInfoResponse.SymbolInfo> activePairs,
            Map<String, Double> quoteVolumeBySymbol,
            Map<String, String> coinNameBySymbol
    ) {
        BinanceExchangeInfoResponse.SymbolInfo pair = activePairs.get(item.symbol());
        if (pair == null) {
            log.debug("Skipping crypto {} because active {} pair was not found.", item.symbol(), QUOTE_ASSET);
            return Optional.empty();
        }

        double quoteVolume = quoteVolumeBySymbol.getOrDefault(pair.symbol(), 0d);
        if (quoteVolume < properties.crypto().minQuoteVolume()) {
            return Optional.empty();
        }

        try {
            List<List<Object>> candles = getKlines(pair.symbol());
            if (candles.size() < candleLimit()) {
                log.warn("Skipping crypto {} because Binance returned {} candles.", pair.symbol(), candles.size());
                return Optional.empty();
            }

            double open = candleNumber(candles.getFirst(), 1);
            double close = candleNumber(candles.getLast(), 4);
            if (open <= 0) {
                return Optional.empty();
            }

            String name = coinNameBySymbol.getOrDefault(item.symbol(), item.name());
            return Optional.of(new CryptoMovement(
                    pair.symbol(),
                    item.symbol(),
                    name,
                    properties.crypto().scanWindow(),
                    roundDouble(open, 2),
                    roundDouble(close, 2),
                    roundDouble(((close - open) / open) * 100, 2),
                    roundDouble(quoteVolume, 2)
            ));
        } catch (Exception e) {
            log.warn("Skipping crypto {} because quote load failed: {}", pair.symbol(), e.getMessage());
            return Optional.empty();
        }
    }

    private List<List<Object>> getKlines(String symbol) {
        for (int attempt = 1; attempt <= properties.crypto().maxQuoteFetchAttempts(); attempt++) {
            try {
                List<List<Object>> candles = binanceClient.klines(symbol, "1m", candleLimit());
                return candles == null ? List.of() : candles;
            } catch (WebClientResponseException e) {
                if (!isTransientProviderError(e) || attempt == properties.crypto().maxQuoteFetchAttempts()) {
                    throw e;
                }
                log.warn("Transient Binance error for crypto {}: HTTP {}. Retry {}/{}.",
                        symbol, e.getStatusCode().value(), attempt, properties.crypto().maxQuoteFetchAttempts());
                sleepBeforeRetry(symbol);
            }
        }
        return List.of();
    }

    private void recordMovement(CryptoMovement movement) {
        CryptoQuoteEntity quoteEntity = persistQuote(movement, false);
        String text = buildMessage(movement, quoteEntity.getDelta());
        boolean haveSendFlag = false;

        List<AppUserPropertyEntity> usersWatchingCoin = userPropertyService.findUsersWatchingCryptoCoin(movement.baseAsset());
        for (AppUserPropertyEntity userWatchConfig : usersWatchingCoin) {
            Long userId = userWatchConfig.getUser().getId();
            UserAlertSettings alertSettings = userPropertyService.loadAlertSettings(userId);
            double delta = quoteEntity.getDelta();
            double rollingDelta = roundDouble(movement.priceChangePercent(), 2);

            if (!shouldSendForUser(alertSettings, delta, rollingDelta)) {
                log.info("Crypto alert skipped for user {} and symbol {}: delta {}% / threshold {}%, rolling {}% / threshold {}%",
                        userId,
                        movement.baseAsset(),
                        formatPercent(delta),
                        formatPercent(alertSettings.deltaThreshold()),
                        formatPercent(rollingDelta),
                        formatPercent(alertSettings.rollingThreshold()));
                continue;
            }
            if (hasAlertAlreadyBeenSent(userId, movement.baseAsset(), quoteEntity)) {
                log.info("Crypto alert skipped for user {} and symbol {}: quote {} was already sent.",
                        userId, movement.baseAsset(), quoteEntity.getId());
                continue;
            }
            if (notificationService.sendCryptoAlert(userId, movement.baseAsset(), quoteEntity.getId(), text)) {
                haveSendFlag = true;
            }
        }

        if (haveSendFlag) {
            quoteEntity.setAlert(true);
            quoteEntity.setSend(true);
            cryptoQuoteRepository.save(quoteEntity);
        }

        cryptoDashboardService.recordResult(new CryptoScanResult(
                movement.symbol(),
                movement.baseAsset(),
                movement.name(),
                movement.window(),
                movement.openPrice(),
                movement.closePrice(),
                movement.priceChangePercent(),
                movement.quoteVolume(),
                direction(movement.priceChangePercent()),
                haveSendFlag,
                Instant.now(),
                haveSendFlag ? text : null
        ));
    }

    private boolean hasAlertAlreadyBeenSent(Long userId, String symbol, CryptoQuoteEntity quoteEntity) {
        if (quoteEntity.getId() == null) {
            return false;
        }
        return cryptoAlertStateRepository.findByUserIdAndSymbolIgnoreCase(userId, symbol)
                .map(state -> state.getLastSentQuote() != null
                        && Objects.equals(state.getLastSentQuote().getId(), quoteEntity.getId()))
                .orElse(false);
    }

    private CryptoQuoteEntity persistQuote(CryptoMovement movement, boolean alert) {
        CryptoQuoteEntity previous = cryptoQuoteRepository.findFirstBySymbolOrderByCreatedDesc(movement.symbol()).orElse(null);
        CryptoQuoteEntity entity = new CryptoQuoteEntity();
        entity.setSymbol(movement.symbol());
        entity.setBaseAsset(movement.baseAsset());
        entity.setCoinName(movement.name());
        entity.setWindow(movement.window());
        entity.setOpenPrice(movement.openPrice());
        entity.setClosePrice(movement.closePrice());
        entity.setHighPrice(Math.max(movement.openPrice(), movement.closePrice()));
        entity.setLowPrice(Math.min(movement.openPrice(), movement.closePrice()));
        entity.setPriceChangePercent(movement.priceChangePercent());
        entity.setDelta(previous == null ? 0 : roundDouble(movement.priceChangePercent() - previous.getPriceChangePercent(), 2));
        entity.setQuoteVolume(movement.quoteVolume());
        entity.setAlert(alert);
        return cryptoQuoteRepository.save(entity);
    }

    private MarketDirection direction(double priceChangePercent) {
        if (priceChangePercent > 0) {
            return MarketDirection.UP;
        }
        if (priceChangePercent < 0) {
            return MarketDirection.DOWN;
        }
        return MarketDirection.NEUTRAL;
    }

    private String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String buildMessage(CryptoMovement movement, double delta) {
        boolean up = movement.priceChangePercent() >= 0;
        String direction = up ? "UP" : "DOWN";
        String icon = up ? "✅" : "❌";
        String coinMark = coinMark(movement.baseAsset());
        return """
                %s %s CRYPTO %s %s
                %s (%s)
                Window: %s
                Move: %.2f%%
                Move since last check: %.2f%%
                Price: %.2f -> %.2f
                24h quote volume: %.2f USDT
                """.formatted(
                icon,
                coinMark,
                movement.symbol(),
                direction,
                movement.name(),
                movement.baseAsset(),
                movement.window(),
                movement.priceChangePercent(),
                delta,
                movement.openPrice(),
                movement.closePrice(),
                movement.quoteVolume()
        );
    }

    private String coinMark(String baseAsset) {
        if ("BTC".equalsIgnoreCase(baseAsset)) {
            return "₿";
        }
        if (baseAsset == null || baseAsset.isBlank()) {
            return "COIN";
        }
        return baseAsset.toUpperCase(Locale.ROOT);
    }

    private int candleLimit() {
        return switch (properties.crypto().scanWindow()) {
            case "1m" -> 1;
            case "5m" -> 5;
            case "15m" -> 15;
            default -> throw new IllegalStateException("Unsupported crypto scan window: " + properties.crypto().scanWindow());
        };
    }

    private double candleNumber(List<Object> candle, int index) {
        Object value = candle.get(index);
        return switch (value) {
            case Number number -> number.doubleValue();
            case String string -> parseDouble(string);
            default -> throw new IllegalArgumentException("Unsupported candle value: " + Objects.toString(value));
        };
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean isLeveragedToken(String baseAsset) {
        if (baseAsset == null) {
            return false;
        }
        return LEVERAGED_SUFFIXES.stream().anyMatch(baseAsset::endsWith);
    }

    private boolean isTransientProviderError(WebClientResponseException e) {
        int statusCode = e.getStatusCode().value();
        return statusCode == 429 || e.getStatusCode().is5xxServerError();
    }

    private void sleepBeforeRetry(String symbol) {
        try {
            Thread.sleep(properties.crypto().quoteFetchRetryDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry crypto quote for symbol " + symbol, e);
        }
    }
}
