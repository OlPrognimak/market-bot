package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.entity.StockCatalogEntity;
import com.prognimak.marketbot.entity.StockTrendProfileEntity;
import com.prognimak.marketbot.repository.StockTrendProfileRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockTrendProfileServiceTest {
    private final StockTrendProfileRepository repository = mock(StockTrendProfileRepository.class);

    @Test
    void usesValidAdaptiveProfile() {
        StockTrendProfileEntity profile = profile(0.70, 0.30, 0.80, 120, 12, Instant.now().plusSeconds(3600));
        when(repository.findAllByStockEnabledTrue()).thenReturn(List.of(profile));
        StockTrendProfileService service = new StockTrendProfileService(repository, MarketTrendServiceTest.properties());

        service.refreshCache();

        assertEquals("STABLE", service.parametersFor("aapl").profileName());
        assertEquals(0.30, service.parametersFor("AAPL").shortWeight(), 0.0001);
    }

    @Test
    void fallsBackWhenProfileHasLowConfidence() {
        StockTrendProfileEntity profile = profile(0.70, 0.30, 0.40, 120, 12, Instant.now().plusSeconds(3600));
        when(repository.findAllByStockEnabledTrue()).thenReturn(List.of(profile));
        StockTrendProfileService service = new StockTrendProfileService(repository, MarketTrendServiceTest.properties());

        service.refreshCache();

        assertEquals("DEFAULT", service.parametersFor("AAPL").profileName());
        assertEquals(0.65, service.parametersFor("AAPL").shortWeight(), 0.0001);
    }

    private StockTrendProfileEntity profile(double longWeight, double shortWeight, double confidence,
                                             int sampleSize, int tradingDays, Instant validUntil) {
        StockCatalogEntity stock = new StockCatalogEntity();
        stock.setSymbol("AAPL");
        StockTrendProfileEntity profile = new StockTrendProfileEntity();
        profile.setStock(stock);
        profile.setProfileName("STABLE");
        profile.setLongWeight(longWeight);
        profile.setShortWeight(shortWeight);
        profile.setMinimumScorePercent(0.2);
        profile.setConfidence(confidence);
        profile.setSampleSize(sampleSize);
        profile.setTradingDays(tradingDays);
        profile.setCalculatedAt(Instant.now());
        profile.setValidUntil(validUntil);
        return profile;
    }
}
