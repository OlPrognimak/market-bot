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
