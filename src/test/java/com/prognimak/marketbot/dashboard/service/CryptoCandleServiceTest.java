package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.client.BinanceClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class CryptoCandleServiceTest {
    private final CryptoCandleService service = new CryptoCandleService(mock(BinanceClient.class));

    @Test
    void mapsBinanceKlinesToProviderCandles() {
        var candles = service.mapCandles(List.of(
                List.of(1_000L, "100.0", "105.0", "99.0", "103.0", "42.5")
        ));

        assertEquals(1, candles.size());
        assertEquals(100.0, candles.getFirst().open());
        assertEquals(105.0, candles.getFirst().high());
        assertEquals(99.0, candles.getFirst().low());
        assertEquals(103.0, candles.getFirst().close());
        assertEquals(42.5, candles.getFirst().volume());
    }
}
