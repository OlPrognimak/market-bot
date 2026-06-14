package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.dashboard.model.MarketDirection;
import com.prognimak.marketbot.config.TrendProperties;
import com.prognimak.marketbot.repository.QuoteRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class MarketTrendServiceTest {
    private final TrendProperties properties = properties();
    private final MarketTrendService service = new MarketTrendService(
            mock(QuoteRepository.class),
            properties,
            mock(StockTrendProfileService.class)
    );

    @Test
    void detectsUpwardTrend() {
        assertEquals(MarketDirection.UP, service.detect(List.of(100.0, 100.1, 100.25, 100.5, 100.8)));
    }

    @Test
    void detectsDownwardTrend() {
        assertEquals(MarketDirection.DOWN, service.detect(List.of(100.8, 100.6, 100.4, 100.1, 99.8)));
    }

    @Test
    void keepsSidewaysMovementNeutral() {
        assertEquals(MarketDirection.NEUTRAL, service.detect(List.of(100.0, 100.03, 99.99, 100.02, 100.01)));
    }

    @Test
    void keepsUnconfirmedMovementNeutral() {
        assertEquals(MarketDirection.NEUTRAL, service.detect(List.of(100.0, 100.5)));
    }

    @Test
    void adaptiveWeightsCanPreferStableLongMovement() {
        var stable = new TrendProperties.TrendParameters("STABLE", 0.70, 0.30, 0.15, 0.8);

        assertEquals(MarketDirection.NEUTRAL,
                service.detect(List.of(100.0, 100.6, 100.8, 100.7, 100.5), stable, 3));
    }

    static TrendProperties properties() {
        return new TrendProperties(true, true, "0 15 2 * * *", "Europe/Berlin",
                15, 5, 5, 30, 10, 100,
                0.35, 0.65, 0.15, 0.25, 0.80, 0.55,
                2.0, 0.05, 1.0, 7,
                0.40, 0.60, 0.01, 0.02, 0.10);
    }
}
