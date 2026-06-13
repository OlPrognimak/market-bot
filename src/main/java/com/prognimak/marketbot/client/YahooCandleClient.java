package com.prognimak.marketbot.client;

import com.prognimak.marketbot.dashboard.model.MarketCandlePoint;
import com.prognimak.marketbot.dashboard.model.MarketCandleResponse;
import com.prognimak.marketbot.dashboard.model.MarketChartRange;
import com.prognimak.marketbot.model.YahooChartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class YahooCandleClient {
    private static final List<String> HOSTS = List.of("query1.finance.yahoo.com", "query2.finance.yahoo.com");
    private static final ZoneId DEFAULT_MARKET_ZONE = ZoneId.of("America/New_York");

    private final WebClient.Builder builder;

    public MarketCandleResponse candles(String symbol, MarketChartRange range) {
        CandleRequest request = CandleRequest.forRange(range);
        YahooChartResponse response = loadChart(symbol, request);
        if (response == null || response.chart() == null || response.chart().result() == null
                || response.chart().result().isEmpty()) {
            throw new IllegalStateException("No Yahoo Finance candle data for symbol: " + symbol);
        }

        YahooChartResponse.Result result = response.chart().result().getFirst();
        List<MarketCandlePoint> points = mapCandles(result);
        ZoneId marketZone = marketZone(result.meta());
        LocalDate today = LocalDate.now(marketZone);
        boolean fallback = range == MarketChartRange.TODAY
                && !points.isEmpty()
                && points.getLast().time().atZone(marketZone).toLocalDate().isBefore(today);
        Instant requestedFrom = range.start(marketZone);

        return new MarketCandleResponse(
                result.meta() == null || result.meta().symbol() == null ? symbol : result.meta().symbol(),
                range.name().toLowerCase(Locale.ROOT),
                request.interval(),
                marketZone.getId(),
                requestedFrom,
                points.isEmpty() ? null : points.getFirst().time(),
                points.isEmpty() ? null : points.getLast().time(),
                fallback,
                points
        );
    }

    private YahooChartResponse loadChart(String symbol, CandleRequest request) {
        RuntimeException firstFailure = null;
        for (String host : HOSTS) {
            try {
                YahooChartResponse response = request(host, symbol, request);
                if (response != null && response.chart() != null && response.chart().result() != null
                        && !response.chart().result().isEmpty()) {
                    return response;
                }
            } catch (RuntimeException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                } else {
                    firstFailure.addSuppressed(exception);
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
        return null;
    }

    private YahooChartResponse request(String host, String symbol, CandleRequest request) {
        return builder.build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host(host)
                        .path("/v8/finance/chart/{symbol}")
                        .queryParam("range", request.providerRange())
                        .queryParam("interval", request.interval())
                        .queryParam("includePrePost", "true")
                        .build(symbol)
                )
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .retrieve()
                .bodyToMono(YahooChartResponse.class)
                .block();
    }

    List<MarketCandlePoint> mapCandles(YahooChartResponse.Result result) {
        if (result.timestamp() == null || result.indicators() == null
                || result.indicators().quote() == null || result.indicators().quote().isEmpty()) {
            return List.of();
        }

        YahooChartResponse.QuoteData quote = result.indicators().quote().getFirst();
        List<MarketCandlePoint> points = new ArrayList<>();
        for (int index = 0; index < result.timestamp().size(); index++) {
            Double open = valueAt(quote.open(), index);
            Double high = valueAt(quote.high(), index);
            Double low = valueAt(quote.low(), index);
            Double close = valueAt(quote.close(), index);
            if (open == null || high == null || low == null || close == null) {
                continue;
            }
            points.add(new MarketCandlePoint(
                    Instant.ofEpochSecond(result.timestamp().get(index)),
                    open,
                    high,
                    low,
                    close,
                    valueAtOrZero(quote.volume(), index)
            ));
        }
        return points;
    }

    private ZoneId marketZone(YahooChartResponse.Meta meta) {
        if (meta == null || meta.exchangeTimezoneName() == null || meta.exchangeTimezoneName().isBlank()) {
            return DEFAULT_MARKET_ZONE;
        }
        try {
            return ZoneId.of(meta.exchangeTimezoneName());
        } catch (RuntimeException ignored) {
            return DEFAULT_MARKET_ZONE;
        }
    }

    private Double valueAt(List<Double> values, int index) {
        return values == null || index < 0 || index >= values.size() ? null : values.get(index);
    }

    private double valueAtOrZero(List<Double> values, int index) {
        Double value = valueAt(values, index);
        return value == null ? 0 : value;
    }

    private record CandleRequest(String providerRange, String interval) {
        private static CandleRequest forRange(MarketChartRange range) {
            return switch (range) {
                case TODAY, YESTERDAY -> new CandleRequest("5d", "5m");
                case WEEK -> new CandleRequest("5d", "15m");
                case MONTH -> new CandleRequest("1mo", "1h");
                case YEAR -> new CandleRequest("1y", "1d");
            };
        }
    }
}
