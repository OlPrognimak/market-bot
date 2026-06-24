package com.prognimak.marketbot.client;

import com.prognimak.marketbot.model.Quote;
import com.prognimak.marketbot.model.MarketSession;
import com.prognimak.marketbot.model.YahooChartResponse;
import com.prognimak.marketbot.model.YahooSessionQuote;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class YahooFinanceClientTest {

    private final YahooFinanceClient client = new YahooFinanceClient(WebClient.builder());

    @Test
    void mapChartResultUsesChartPreviousCloseWhenMetaPreviousCloseIsMissing() {
        YahooChartResponse.Result result = result(
                new YahooChartResponse.Meta(
                        "OUST",
                        33.12,
                        null,
                        23.94
                ),
                List.of(23.94, 25.50, 30.00, 33.11, 33.12)
        );

        Quote quote = client.mapChartResult("OUST", result);

        assertAll(
                () -> assertEquals(33.12, quote.current()),
                () -> assertEquals(23.94, quote.previousClose()),
                () -> assertEquals(9.18, quote.change()),
                () -> assertEquals(38.35, quote.percentChange()),
                () -> assertEquals(35.00, quote.high()),
                () -> assertEquals(31.00, quote.low()),
                () -> assertEquals(32.50, quote.open())
        );
    }

    @Test
    void mapChartResultPrefersMetaPreviousCloseWhenPresent() {
        YahooChartResponse.Result result = result(
                new YahooChartResponse.Meta(
                        "OUST",
                        33.12,
                        34.86,
                        23.94
                ),
                List.of(23.94, 25.50, 30.00, 33.11, 33.12)
        );

        Quote quote = client.mapChartResult("OUST", result);

        assertAll(
                () -> assertEquals(34.86, quote.previousClose()),
                () -> assertEquals(-1.74, quote.change()),
                () -> assertEquals(-4.99, quote.percentChange())
        );
    }

    @Test
    void mapSessionQuoteClassifiesPreMarketAndUsesPreviousCloseBaseline() {
        long timestamp = Instant.now().getEpochSecond();
        YahooChartResponse.TradingPeriods periods = new YahooChartResponse.TradingPeriods(
                new YahooChartResponse.TradingPeriod("EDT", timestamp - 60, timestamp + 60, -14_400),
                new YahooChartResponse.TradingPeriod("EDT", timestamp + 61, timestamp + 10_000, -14_400),
                null
        );
        YahooChartResponse.Meta meta = new YahooChartResponse.Meta(
                "AAPL", 201.0, 200.0, 200.0, timestamp, "NMS", "America/New_York", -14_400, periods
        );
        YahooChartResponse.QuoteData data = new YahooChartResponse.QuoteData(
                List.of(201.0), List.of(202.0), List.of(200.5), List.of(202.0), List.of(1_000.0)
        );
        YahooSessionQuote quote = client.mapSessionQuote("AAPL", new YahooChartResponse.Result(
                meta, List.of(timestamp), new YahooChartResponse.Indicators(List.of(data))
        ));

        assertAll(
                () -> assertEquals(MarketSession.PRE_MARKET, quote.session()),
                () -> assertEquals(MarketSession.PRE_MARKET, quote.activeSession()),
                () -> assertEquals(202.0, quote.price()),
                () -> assertEquals(200.0, quote.baselinePrice()),
                () -> assertEquals(1.0, quote.changePercent()),
                () -> assertEquals(1_000.0, quote.volume())
        );
    }

    @Test
    void mapSessionQuoteIgnoresSuspiciousLowVolumePreMarketOutlier() {
        long timestamp = Instant.now().getEpochSecond();
        YahooChartResponse.TradingPeriods periods = new YahooChartResponse.TradingPeriods(
                new YahooChartResponse.TradingPeriod("EDT", timestamp - 600, timestamp + 60, -14_400),
                new YahooChartResponse.TradingPeriod("EDT", timestamp + 61, timestamp + 10_000, -14_400),
                null
        );
        YahooChartResponse.Meta meta = new YahooChartResponse.Meta(
                "AMZN", 201.0, 200.0, 200.0, timestamp, "NMS", "America/New_York", -14_400, periods
        );
        YahooChartResponse.QuoteData data = new YahooChartResponse.QuoteData(
                List.of(198.0, 178.0),
                List.of(198.0, 178.0),
                List.of(198.0, 178.0),
                List.of(198.0, 178.0),
                List.of(5_000.0, 5.0)
        );

        YahooSessionQuote quote = client.mapSessionQuote("AMZN", new YahooChartResponse.Result(
                meta, List.of(timestamp - 60, timestamp), new YahooChartResponse.Indicators(List.of(data))
        ));

        assertAll(
                () -> assertEquals(MarketSession.PRE_MARKET, quote.session()),
                () -> assertEquals(198.0, quote.price()),
                () -> assertEquals(200.0, quote.baselinePrice()),
                () -> assertEquals(-1.0, quote.changePercent()),
                () -> assertEquals(5_000.0, quote.volume()),
                () -> assertEquals(Instant.ofEpochSecond(timestamp - 60), quote.providerTimestamp())
        );
    }

    @Test
    void mapSessionQuoteFallsBackToLatestCloseWhenVolumeIsMissing() {
        long timestamp = Instant.now().getEpochSecond();
        YahooChartResponse.TradingPeriods periods = new YahooChartResponse.TradingPeriods(
                new YahooChartResponse.TradingPeriod("EDT", timestamp - 60, timestamp + 60, -14_400),
                new YahooChartResponse.TradingPeriod("EDT", timestamp + 61, timestamp + 10_000, -14_400),
                null
        );
        YahooChartResponse.Meta meta = new YahooChartResponse.Meta(
                "AAPL", 201.0, 200.0, 200.0, timestamp, "NMS", "America/New_York", -14_400, periods
        );
        YahooChartResponse.QuoteData data = new YahooChartResponse.QuoteData(
                List.of(201.0, 202.0),
                List.of(201.0, 202.0),
                List.of(201.0, 202.0),
                List.of(201.0, 202.0),
                null
        );

        YahooSessionQuote quote = client.mapSessionQuote("AAPL", new YahooChartResponse.Result(
                meta, List.of(timestamp - 60, timestamp), new YahooChartResponse.Indicators(List.of(data))
        ));

        assertAll(
                () -> assertEquals(202.0, quote.price()),
                () -> assertEquals(200.0, quote.baselinePrice()),
                () -> assertEquals(1.0, quote.changePercent()),
                () -> assertEquals(0.0, quote.volume()),
                () -> assertEquals(Instant.ofEpochSecond(timestamp), quote.providerTimestamp())
        );
    }

    @Test
    void mapSessionQuoteIgnoresExtremePreMarketOutlierWhenVolumeIsMissing() {
        long timestamp = Instant.now().getEpochSecond();
        YahooChartResponse.TradingPeriods periods = new YahooChartResponse.TradingPeriods(
                new YahooChartResponse.TradingPeriod("EDT", timestamp - 600, timestamp + 60, -14_400),
                new YahooChartResponse.TradingPeriod("EDT", timestamp + 61, timestamp + 10_000, -14_400),
                null
        );
        YahooChartResponse.Meta meta = new YahooChartResponse.Meta(
                "AMZU", 35.43, 35.43, 35.43, timestamp, "NMS", "America/New_York", -14_400, periods
        );
        YahooChartResponse.QuoteData data = new YahooChartResponse.QuoteData(
                List.of(35.07, 31.18),
                List.of(35.07, 31.18),
                List.of(35.07, 31.18),
                List.of(35.07, 31.18),
                Arrays.asList(null, null)
        );

        YahooSessionQuote quote = client.mapSessionQuote("AMZU", new YahooChartResponse.Result(
                meta, List.of(timestamp - 60, timestamp), new YahooChartResponse.Indicators(List.of(data))
        ));

        assertAll(
                () -> assertEquals(MarketSession.PRE_MARKET, quote.session()),
                () -> assertEquals(35.07, quote.price()),
                () -> assertEquals(35.43, quote.baselinePrice()),
                () -> assertEquals(-1.02, quote.changePercent()),
                () -> assertEquals(0.0, quote.volume()),
                () -> assertEquals(Instant.ofEpochSecond(timestamp - 60), quote.providerTimestamp())
        );
    }

    @Test
    void mapSessionQuoteUsesRegularMarketPriceAsPostMarketBaseline() {
        long timestamp = Instant.now().getEpochSecond();
        YahooChartResponse.TradingPeriods periods = new YahooChartResponse.TradingPeriods(
                null,
                new YahooChartResponse.TradingPeriod("EDT", timestamp - 10_000, timestamp - 61, -14_400),
                new YahooChartResponse.TradingPeriod("EDT", timestamp - 60, timestamp + 60, -14_400)
        );
        YahooChartResponse.Meta meta = new YahooChartResponse.Meta(
                "MSFT", 205.0, 200.0, 200.0, timestamp, "NMS", "America/New_York", -14_400, periods
        );
        YahooChartResponse.QuoteData data = new YahooChartResponse.QuoteData(
                List.of(206.0),
                List.of(206.0),
                List.of(206.0),
                List.of(206.0),
                List.of(2_000.0)
        );

        YahooSessionQuote quote = client.mapSessionQuote("MSFT", new YahooChartResponse.Result(
                meta, List.of(timestamp), new YahooChartResponse.Indicators(List.of(data))
        ));

        assertAll(
                () -> assertEquals(MarketSession.POST_MARKET, quote.session()),
                () -> assertEquals(MarketSession.POST_MARKET, quote.activeSession()),
                () -> assertEquals(206.0, quote.price()),
                () -> assertEquals(205.0, quote.baselinePrice()),
                () -> assertEquals(0.49, quote.changePercent()),
                () -> assertEquals(2_000.0, quote.volume())
        );
    }

    private static YahooChartResponse.Result result(
            YahooChartResponse.Meta meta,
            List<Double> closes
    ) {
        YahooChartResponse.QuoteData quoteData = new YahooChartResponse.QuoteData(
                List.of(32.50, 33.00, 33.00, 33.00, 33.00),
                List.of(33.00, 34.00, 35.00, 34.00, 33.50),
                List.of(32.00, 31.00, 32.00, 32.00, 32.00),
                closes
        );
        return new YahooChartResponse.Result(
                meta,
                List.of(),
                new YahooChartResponse.Indicators(List.of(quoteData))
        );
    }
}
