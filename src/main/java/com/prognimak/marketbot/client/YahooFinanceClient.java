package com.prognimak.marketbot.client;


import com.prognimak.marketbot.model.Quote;
import com.prognimak.marketbot.model.MarketSession;
import com.prognimak.marketbot.model.YahooChartResponse;
import com.prognimak.marketbot.model.YahooSessionQuote;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Objects;
import java.time.Instant;

import static com.prognimak.marketbot.util.Utils.roundDouble;

@Service
@RequiredArgsConstructor
public class YahooFinanceClient implements MarketDataProvider {
    private static final double EXTENDED_HOURS_OUTLIER_THRESHOLD_PERCENT = 8.0;
    private static final double EXTENDED_HOURS_LOW_VOLUME_THRESHOLD = 1_000.0;
    private static final List<String> PUBLIC_CHART_HOSTS = List.of(
            "query1.finance.yahoo.com",
            "query2.finance.yahoo.com"
    );

    private final WebClient.Builder builder;

    public Quote getQuote(String symbol) {
        RuntimeException firstFailure = null;
        for (String host : PUBLIC_CHART_HOSTS) {
            try {
                return getQuote(host, symbol);
            } catch (RuntimeException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                } else {
                    firstFailure.addSuppressed(exception);
                }
            }
        }
        throw firstFailure == null
                ? new IllegalStateException("No Yahoo Finance public chart host is configured")
                : firstFailure;
    }

    public YahooSessionQuote getSessionQuote(String symbol) {
        RuntimeException firstFailure = null;
        for (String host : PUBLIC_CHART_HOSTS) {
            try {
                return getSessionQuote(host, symbol);
            } catch (RuntimeException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                } else {
                    firstFailure.addSuppressed(exception);
                }
            }
        }
        throw firstFailure == null
                ? new IllegalStateException("No Yahoo Finance public chart host is configured")
                : firstFailure;
    }

    private Quote getQuote(String host, String symbol) {
        YahooChartResponse response = loadChart(host, symbol, false);
        if (response == null || response.chart() == null) {
            throw new IllegalStateException("No Yahoo Finance data for symbol: " + symbol);
        }

        List<YahooChartResponse.Result> results = response.chart().result();
        if (results == null || results.isEmpty()) {
            throw new IllegalStateException(
                    "No Yahoo Finance chart result for symbol: "
                            + symbol
                            + ": "
                            + response.chart().error()
            );
        }

        return mapChartResult(symbol, results.getFirst());
    }

    private YahooSessionQuote getSessionQuote(String host, String symbol) {
        YahooChartResponse response = loadChart(host, symbol, true);
        if (response == null || response.chart() == null || response.chart().result() == null || response.chart().result().isEmpty()) {
            throw new IllegalStateException("No Yahoo Finance extended-hours data for symbol: " + symbol);
        }
        return mapSessionQuote(symbol, response.chart().result().getFirst());
    }

    private YahooChartResponse loadChart(String host, String symbol, boolean includePrePost) {
        return builder.build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host(host)
                        .path("/v8/finance/chart/{symbol}")
                        .queryParam("range", "1d")
                        .queryParam("interval", "1m")
                        .queryParam("includePrePost", includePrePost)
                        .build(symbol)
                )
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .retrieve()
                .bodyToMono(YahooChartResponse.class)
                .block();
    }

    Quote mapChartResult(String requestedSymbol, YahooChartResponse.Result result) {
        YahooChartResponse.Meta meta = result.meta();
        YahooChartResponse.Indicators indicators = result.indicators();

        if (meta == null || indicators == null || indicators.quote() == null || indicators.quote().isEmpty()) {
            throw new IllegalStateException("No Yahoo Finance quote candles for symbol: " + requestedSymbol);
        }

        YahooChartResponse.QuoteData quote = indicators.quote().getFirst();
        double current = valueOrLastClose(meta.regularMarketPrice(), quote.close());
        int latestIndex = latestNumericIndex(quote.close());
        double previousClose = previousClose(meta.previousClose(), meta.chartPreviousClose(), current);
        double change = current - previousClose;
        double percentChange = previousClose == 0 ? 0 : (change / previousClose) * 100;
        double dayHigh = maxOrFallback(quote.high(), current);
        double dayLow = minOrFallback(quote.low(), current);
        double dayOpen = firstOrFallback(quote.open(), current);

        return new Quote(
                meta.symbol() == null ? requestedSymbol : meta.symbol(),
                roundDouble(current, 2),
                roundDouble(change, 2),
                roundDouble(percentChange, 2),
                roundDouble(dayHigh, 2),
                roundDouble(dayLow, 2),
                roundDouble(dayOpen, 2),
                roundDouble(previousClose, 2)
        );
    }

    YahooSessionQuote mapSessionQuote(String requestedSymbol, YahooChartResponse.Result result) {
        YahooChartResponse.Meta meta = result.meta();
        YahooChartResponse.Indicators indicators = result.indicators();
        if (meta == null || indicators == null || indicators.quote() == null || indicators.quote().isEmpty()) {
            throw new IllegalStateException("No Yahoo Finance session candles for symbol: " + requestedSymbol);
        }
        YahooChartResponse.QuoteData quote = indicators.quote().getFirst();
        int latestIndex = latestSessionPriceIndex(meta, quote, result.timestamp());
        if (latestIndex < 0 || result.timestamp() == null || latestIndex >= result.timestamp().size()) {
            throw new IllegalStateException("No timestamped Yahoo Finance session price for symbol: " + requestedSymbol);
        }
        long timestamp = result.timestamp().get(latestIndex);
        double price = quote.close().get(latestIndex);
        MarketSession session = session(meta.currentTradingPeriod(), timestamp);
        MarketSession activeSession = session(meta.currentTradingPeriod(), Instant.now().getEpochSecond());
        double baseline = session == MarketSession.POST_MARKET
                ? valueOrLastClose(meta.regularMarketPrice(), quote.close())
                : previousClose(meta.previousClose(), meta.chartPreviousClose(), price);
        double changePercent = baseline <= 0 ? 0 : ((price - baseline) / baseline) * 100;
        return new YahooSessionQuote(
                meta.symbol() == null ? requestedSymbol : meta.symbol(),
                session,
                activeSession,
                meta.exchangeName(),
                meta.exchangeTimezoneName(),
                roundDouble(price, 4),
                roundDouble(baseline, 4),
                roundDouble(changePercent, 2),
                roundDouble(valueAtOrFallback(quote.open(), latestIndex, price), 4),
                roundDouble(valueAtOrFallback(quote.high(), latestIndex, price), 4),
                roundDouble(valueAtOrFallback(quote.low(), latestIndex, price), 4),
                roundDouble(valueAtOrFallback(quote.volume(), latestIndex, 0), 2),
                Instant.ofEpochSecond(timestamp)
        );
    }

    private int latestSessionPriceIndex(
            YahooChartResponse.Meta meta,
            YahooChartResponse.QuoteData quote,
            List<Long> timestamps
    ) {
        if (quote.close() == null || timestamps == null) {
            return -1;
        }

        int latestFallbackIndex = -1;
        int latestNonSuspiciousFallbackIndex = -1;
        int latestSuspiciousPositiveVolumeIndex = -1;
        int upperBound = Math.min(quote.close().size(), timestamps.size());
        for (int i = upperBound - 1; i >= 0; i--) {
            Double close = quote.close().get(i);
            if (close == null) {
                continue;
            }
            if (latestFallbackIndex < 0) {
                latestFallbackIndex = i;
            }

            boolean suspicious = isSuspiciousExtendedHoursPrint(meta, quote.close(), quote.volume(), timestamps.get(i), i, close);
            if (!suspicious && latestNonSuspiciousFallbackIndex < 0) {
                latestNonSuspiciousFallbackIndex = i;
            }

            if (positiveVolumeAt(quote.volume(), i)) {
                if (!suspicious) {
                    return i;
                }
                if (latestSuspiciousPositiveVolumeIndex < 0) {
                    latestSuspiciousPositiveVolumeIndex = i;
                }
            }
        }

        if (latestNonSuspiciousFallbackIndex >= 0) {
            return latestNonSuspiciousFallbackIndex;
        }

        return latestFallbackIndex >= 0 ? latestFallbackIndex : latestSuspiciousPositiveVolumeIndex;
    }

    private boolean isSuspiciousExtendedHoursPrint(
            YahooChartResponse.Meta meta,
            List<Double> closes,
            List<Double> volumes,
            long timestamp,
            int index,
            double price
    ) {
        if (!weakVolumeAt(volumes, index)) {
            return false;
        }

        MarketSession session = session(meta.currentTradingPeriod(), timestamp);
        if (session != MarketSession.PRE_MARKET && session != MarketSession.POST_MARKET) {
            return false;
        }

        double baseline = session == MarketSession.POST_MARKET
                ? valueOrLastClose(meta.regularMarketPrice(), closes)
                : previousClose(meta.previousClose(), meta.chartPreviousClose(), price);
        double changePercent = baseline <= 0 ? 0 : ((price - baseline) / baseline) * 100;
        return Math.abs(changePercent) > EXTENDED_HOURS_OUTLIER_THRESHOLD_PERCENT;
    }

    private boolean positiveVolumeAt(List<Double> volumes, int index) {
        Double volume = valueAt(volumes, index);
        return volume != null && volume > 0;
    }

    private boolean weakVolumeAt(List<Double> volumes, int index) {
        Double volume = valueAt(volumes, index);
        return volume == null || volume < EXTENDED_HOURS_LOW_VOLUME_THRESHOLD;
    }

    private Double valueAt(List<Double> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private MarketSession session(YahooChartResponse.TradingPeriods periods, long timestamp) {
        if (periods == null) {
            return MarketSession.UNKNOWN;
        }
        if (inside(periods.pre(), timestamp)) {
            return MarketSession.PRE_MARKET;
        }
        if (inside(periods.regular(), timestamp)) {
            return MarketSession.REGULAR;
        }
        if (inside(periods.post(), timestamp)) {
            return MarketSession.POST_MARKET;
        }
        return MarketSession.CLOSED;
    }

    private boolean inside(YahooChartResponse.TradingPeriod period, long timestamp) {
        return period != null && timestamp >= period.start() && timestamp <= period.end();
    }

    private double valueAtOrFallback(List<Double> values, int index, double fallback) {
        if (values == null || index < 0 || index >= values.size() || values.get(index) == null) {
            return fallback;
        }
        return values.get(index);
    }

    private double valueOrLastClose(Double value, List<Double> closes) {
        if (value != null) {
            return value;
        }

        int latestIndex = latestNumericIndex(closes);
        if (latestIndex >= 0) {
            return closes.get(latestIndex);
        }

        throw new IllegalStateException("No numeric Yahoo Finance price found");
    }

    private double previousClose(
            Double metaPreviousClose,
            Double chartPreviousClose,
            double fallback
    ) {
        if (metaPreviousClose != null) {
            return metaPreviousClose;
        }

        if (chartPreviousClose != null) {
            return chartPreviousClose;
        }

        return fallback;
    }

    private int latestNumericIndex(List<Double> values) {
        if (values == null) {
            return -1;
        }

        for (int i = values.size() - 1; i >= 0; i--) {
            if (values.get(i) != null) {
                return i;
            }
        }

        return -1;
    }

    private double firstOrFallback(List<Double> values, double fallback) {
        if (values == null) {
            return fallback;
        }

        return values.stream()
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(fallback);
    }

    private double minOrFallback(List<Double> values, double fallback) {
        if (values == null) {
            return fallback;
        }

        return values.stream()
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .min()
                .orElse(fallback);
    }

    private double maxOrFallback(List<Double> values, double fallback) {
        if (values == null) {
            return fallback;
        }

        return values.stream()
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(fallback);
    }
}
