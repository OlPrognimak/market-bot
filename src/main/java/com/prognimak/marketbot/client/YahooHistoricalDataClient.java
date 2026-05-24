package com.prognimak.marketbot.client;

import com.prognimak.marketbot.entity.HistoryQuoteEntity;
import com.prognimak.marketbot.model.HistoryIntervalType;
import com.prognimak.marketbot.model.YahooChartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static com.prognimak.marketbot.util.Utils.roundDouble;

@Service
@RequiredArgsConstructor
public class YahooHistoricalDataClient {
    private static final ZoneId TRADING_DATE_ZONE = ZoneId.systemDefault();

    private final WebClient.Builder builder;

    public List<HistoryQuoteEntity> historicalQuotes(
            String symbol,
            LocalDate from,
            LocalDate to,
            String source,
            HistoryIntervalType intervalType
    ) {
        LocalDate requestFrom = intervalType.requestFrom(from);
        long period1 = requestFrom.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long period2 = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        YahooChartResponse response = builder.build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("query1.finance.yahoo.com")
                        .path("/v8/finance/chart/{symbol}")
                        .queryParam("period1", period1)
                        .queryParam("period2", period2)
                        .queryParam("interval", intervalType.yahooInterval())
                        .queryParam("includePrePost", "false")
                        .build(symbol)
                )
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .retrieve()
                .bodyToMono(YahooChartResponse.class)
                .block();

        if (response == null || response.chart() == null || response.chart().result() == null || response.chart().result().isEmpty()) {
            throw new IllegalStateException("No Yahoo Finance historical data for symbol: " + symbol);
        }

        return mapQuotes(symbol, response.chart().result().getFirst(), from, to, source, intervalType);
    }

    private List<HistoryQuoteEntity> mapQuotes(
            String requestedSymbol,
            YahooChartResponse.Result result,
            LocalDate from,
            LocalDate to,
            String source,
            HistoryIntervalType intervalType
    ) {
        if (result.timestamp() == null || result.indicators() == null
                || result.indicators().quote() == null || result.indicators().quote().isEmpty()) {
            throw new IllegalStateException("No Yahoo Finance historical candles for symbol: " + requestedSymbol);
        }

        YahooChartResponse.QuoteData quote = result.indicators().quote().getFirst();
        String providerSymbol = result.meta() == null || result.meta().symbol() == null
                ? requestedSymbol
                : result.meta().symbol();

        List<HistoryQuoteEntity> entities = new ArrayList<>();
        Double previousClose = null;

        for (int i = 0; i < result.timestamp().size(); i++) {
            Double close = valueAt(quote.close(), i);
            Double open = valueAt(quote.open(), i);
            Double high = valueAt(quote.high(), i);
            Double low = valueAt(quote.low(), i);

            if (close == null || open == null || high == null || low == null) {
                continue;
            }

            LocalDate tradingDate = Instant.ofEpochSecond(result.timestamp().get(i))
                    .atZone(TRADING_DATE_ZONE)
                    .toLocalDate();

            if (!tradingDate.isBefore(from) && !tradingDate.isAfter(to) && previousClose != null) {
                HistoryQuoteEntity entity = new HistoryQuoteEntity();
                entity.setSymbol(requestedSymbol);
                entity.setSourceSymbol(providerSymbol);
                entity.setSource(source);
                entity.setIntervalType(intervalType.name());
                entity.setTradingDate(tradingDate);
                entity.setCurrent(roundDouble(close, 2));
                entity.setOpen(roundDouble(open, 2));
                entity.setHigh(roundDouble(high, 2));
                entity.setLow(roundDouble(low, 2));
                entity.setPreviousClose(roundDouble(previousClose, 2));
                entity.setChange(roundDouble(close - previousClose, 2));
                entity.setPercentChange(previousClose == 0 ? 0 : roundDouble(((close - previousClose) / previousClose) * 100, 2));
                entity.setDelta(0);
                entity.setSend(false);
                entity.setValidated(false);
                entities.add(entity);
            }

            previousClose = close;
        }

        return entities;
    }

    private Double valueAt(List<Double> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }

        return values.get(index);
    }
}
