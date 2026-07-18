package com.prognimak.marketbot.service;

import com.prognimak.marketbot.client.YahooFinanceClient;
import com.prognimak.marketbot.entity.StockCatalogEntity;
import com.prognimak.marketbot.model.StockSymbolMetadata;
import com.prognimak.marketbot.model.WatchlistPriority;
import com.prognimak.marketbot.portfolio.service.ProviderSymbolMappingService;
import com.prognimak.marketbot.repository.StockCatalogRepository;
import com.prognimak.marketbot.user.model.CatalogItemRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockCatalogServiceTest {

    @Mock
    private StockCatalogRepository repository;
    @Mock
    private YahooFinanceClient yahooFinanceClient;
    @Mock
    private ProviderSymbolMappingService providerSymbolMappingService;

    private StockCatalogService service;

    @BeforeEach
    void setUp() {
        service = new StockCatalogService(repository, yahooFinanceClient, providerSymbolMappingService);
    }

    @Test
    void createValidatesWithYahooEnrichesCatalogAndCreatesPortfolioMappings() {
        when(repository.existsBySymbolIgnoreCase("AAPL")).thenReturn(false);
        when(yahooFinanceClient.getStockMetadata("AAPL")).thenReturn(new StockSymbolMetadata(
                "AAPL", "Apple Inc.", "US", null, "NasdaqGS", "USD"
        ));
        when(repository.save(any(StockCatalogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new CatalogItemRequest("aapl", "", null, null, null, null, true, null));

        assertAll(
                () -> assertEquals("AAPL", response.symbol()),
                () -> assertEquals("Apple Inc.", response.name()),
                () -> assertEquals("US", response.region()),
                () -> assertEquals("NasdaqGS", response.exchange()),
                () -> assertEquals("USD", response.currency()),
                () -> assertEquals(WatchlistPriority.NORMAL.name(), response.priority())
        );
        verify(providerSymbolMappingService).ensureTickerMappingsForStock("AAPL", "Apple Inc.", "USD");
    }

    @Test
    void ensureProviderSymbolCreatesEnrichedCatalogItemAndMappingsWhenMissing() {
        when(repository.findBySymbolIgnoreCase("MSFT")).thenReturn(Optional.empty());
        when(yahooFinanceClient.getStockMetadata("MSFT")).thenReturn(new StockSymbolMetadata(
                "MSFT", "Microsoft Corporation", "US", null, "NasdaqGS", "USD"
        ));
        when(repository.save(any(StockCatalogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockCatalogEntity entity = service.ensureProviderSymbol("msft", "MSFT");

        assertAll(
                () -> assertEquals("MSFT", entity.getSymbol()),
                () -> assertEquals("Microsoft Corporation", entity.getName()),
                () -> assertEquals("US", entity.getRegion()),
                () -> assertEquals("NasdaqGS", entity.getExchange()),
                () -> assertEquals("USD", entity.getCurrency())
        );
        verify(providerSymbolMappingService).ensureTickerMappingsForStock("MSFT", "Microsoft Corporation", "USD");
    }
}
