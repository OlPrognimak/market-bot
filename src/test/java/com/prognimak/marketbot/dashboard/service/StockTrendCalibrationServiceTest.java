package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.config.TrendProperties;
import com.prognimak.marketbot.entity.QuoteEntity;
import com.prognimak.marketbot.entity.StockCatalogEntity;
import com.prognimak.marketbot.entity.StockTrendProfileEntity;
import com.prognimak.marketbot.repository.QuoteRepository;
import com.prognimak.marketbot.repository.StockCatalogRepository;
import com.prognimak.marketbot.repository.StockTrendProfileRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class StockTrendCalibrationServiceTest {
    @Test
    void selectsProfileFromHistoricalScannerData() {
        TrendProperties properties = properties();
        QuoteRepository quoteRepository = mock(QuoteRepository.class);
        MarketTrendService trendService = new MarketTrendService(
                quoteRepository, properties, mock(StockTrendProfileService.class));
        StockTrendCalibrationService service = new StockTrendCalibrationService(
                properties,
                mock(StockCatalogRepository.class),
                mock(StockTrendProfileRepository.class),
                quoteRepository,
                trendService,
                mock(StockTrendProfileService.class)
        );
        StockCatalogEntity stock = new StockCatalogEntity();
        stock.setSymbol("AAPL");
        Instant start = Instant.parse("2026-06-12T08:00:00Z");
        List<QuoteEntity> quotes = List.of(
                quote(start, 100.0), quote(start.plusSeconds(60), 100.2), quote(start.plusSeconds(120), 100.4),
                quote(start.plusSeconds(180), 100.6), quote(start.plusSeconds(240), 100.8), quote(start.plusSeconds(300), 101.0),
                quote(start.plusSeconds(360), 101.2), quote(start.plusSeconds(420), 101.4), quote(start.plusSeconds(480), 101.6)
        );
        when(quoteRepository.findBySymbolAndCreatedGreaterThanEqualOrderByCreatedAsc(eq("AAPL"), any()))
                .thenReturn(quotes);

        var result = service.calibrate(stock);

        assertNotNull(result);
        assertTrue(result.confidence() > 0);
        assertEquals(1, result.tradingDays());
    }

    @Test
    void createsDefaultProfileWhenHistoryIsInsufficient() {
        TrendProperties properties = properties();
        QuoteRepository quoteRepository = mock(QuoteRepository.class);
        StockCatalogRepository stockRepository = mock(StockCatalogRepository.class);
        StockTrendProfileRepository profileRepository = mock(StockTrendProfileRepository.class);
        StockTrendProfileService profileService = mock(StockTrendProfileService.class);
        MarketTrendService trendService = new MarketTrendService(quoteRepository, properties, profileService);
        StockTrendCalibrationService service = new StockTrendCalibrationService(
                properties, stockRepository, profileRepository, quoteRepository, trendService, profileService);
        StockCatalogEntity stock = new StockCatalogEntity();
        stock.setSymbol("AAPL");
        when(stockRepository.findByEnabledTrueOrderBySymbolAsc()).thenReturn(List.of(stock));
        when(profileRepository.findByStockSymbolIgnoreCase("AAPL")).thenReturn(Optional.empty());
        when(quoteRepository.findBySymbolAndCreatedGreaterThanEqualOrderByCreatedAsc(eq("AAPL"), any()))
                .thenReturn(List.of());

        var summary = service.recalibrateAll();

        ArgumentCaptor<StockTrendProfileEntity> captor = ArgumentCaptor.forClass(StockTrendProfileEntity.class);
        verify(profileRepository).save(captor.capture());
        assertEquals("DEFAULT", captor.getValue().getProfileName());
        assertEquals(0, captor.getValue().getConfidence(), 0.0001);
        assertEquals(1, summary.insufficient());
    }

    private QuoteEntity quote(Instant created, double current) {
        QuoteEntity quote = mock(QuoteEntity.class);
        when(quote.getCreated()).thenReturn(created);
        when(quote.getCurrent()).thenReturn(current);
        when(quote.getPreviousClose()).thenReturn(99.0);
        return quote;
    }

    private TrendProperties properties() {
        return new TrendProperties(true, true, "0 15 2 * * *", "Europe/Berlin",
                3, 2, 1, 30, 1, 1,
                0.35, 0.65, 0.05, 0.25, 0.80, 0.50,
                1.0, 0.01, 1.0, 7,
                0.40, 0.60, 0.01, 0.02, 0.10);
    }
}
