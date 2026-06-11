package com.prognimak.marketbot.client;

import com.prognimak.marketbot.model.YahooChartResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YahooCandleClientTest {
    private final YahooCandleClient client = new YahooCandleClient(WebClient.builder());

    @Test
    void mapsOnlyCompleteProviderCandles() {
        YahooChartResponse.QuoteData data = new YahooChartResponse.QuoteData(
                List.of(100.0, 101.0, 102.0),
                List.of(103.0, 104.0, 105.0),
                List.of(99.0, 100.0, 101.0),
                Arrays.asList(102.0, 103.0, null),
                List.of(10.0, 20.0, 30.0)
        );
        YahooChartResponse.Result result = new YahooChartResponse.Result(
                null,
                List.of(1_000L, 2_000L, 3_000L),
                new YahooChartResponse.Indicators(List.of(data))
        );

        var candles = client.mapCandles(result);

        assertEquals(2, candles.size());
        assertEquals(100.0, candles.getFirst().open());
        assertEquals(103.0, candles.getFirst().high());
        assertEquals(99.0, candles.getFirst().low());
        assertEquals(102.0, candles.getFirst().close());
        assertEquals(10.0, candles.getFirst().volume());
    }
}
