package com.prognimak.marketbot.client;

import com.prognimak.marketbot.model.Quote;
import com.prognimak.marketbot.model.YahooChartResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class YahooFinanceClientTest {

    private final YahooFinanceClient client = new YahooFinanceClient(WebClient.builder());

    @Test
    void mapChartResultUsesPreviousDailyCandleWhenMetaPreviousCloseIsMissing() {
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
                () -> assertEquals(33.11, quote.previousClose()),
                () -> assertEquals(0.01, quote.change()),
                () -> assertEquals(0.03, quote.percentChange())
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

    private static YahooChartResponse.Result result(
            YahooChartResponse.Meta meta,
            List<Double> closes
    ) {
        YahooChartResponse.QuoteData quoteData = new YahooChartResponse.QuoteData(
                List.of(33.00, 33.00, 33.00, 33.00, 33.00),
                List.of(34.00, 34.00, 34.00, 34.00, 34.00),
                List.of(32.00, 32.00, 32.00, 32.00, 32.00),
                closes
        );
        return new YahooChartResponse.Result(
                meta,
                new YahooChartResponse.Indicators(List.of(quoteData))
        );
    }
}
