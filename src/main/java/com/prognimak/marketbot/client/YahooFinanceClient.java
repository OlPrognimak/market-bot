package com.prognimak.marketbot.client;


import com.prognimak.marketbot.model.Quote;
import com.prognimak.marketbot.model.YahooChartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Objects;

import static com.prognimak.marketbot.util.Utils.roundDouble;

@Service
@RequiredArgsConstructor
public class YahooFinanceClient implements MarketDataProvider {

    private final WebClient.Builder builder;


    public Quote getQuote(String symbol) {

        YahooChartResponse response = builder.build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("query1.finance.yahoo.com")
                        .path("/v8/finance/chart/{symbol}")
                        .queryParam("range", "1d")
                        .queryParam("interval", "1m")
                        .build(symbol)
                )
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .retrieve()
                .bodyToMono(YahooChartResponse.class)
                .block();

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
