package com.prognimak.marketbot.portfolio.service;

import com.prognimak.marketbot.client.YahooFinanceClient;
import com.prognimak.marketbot.entity.QuoteEntity;
import com.prognimak.marketbot.entity.StockCatalogEntity;
import com.prognimak.marketbot.model.Quote;
import com.prognimak.marketbot.repository.QuoteRepository;
import com.prognimak.marketbot.repository.StockCatalogRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioMarketPriceServiceTest {
    private final QuoteRepository quoteRepository = mock(QuoteRepository.class);
    private final StockCatalogRepository stockCatalogRepository = mock(StockCatalogRepository.class);
    private final YahooFinanceClient yahooFinanceClient = mock(YahooFinanceClient.class);
    private final PortfolioMarketPriceService service =
            new PortfolioMarketPriceService(quoteRepository, stockCatalogRepository, yahooFinanceClient);

    @Test
    void resolvesRevolutBaseTickerFromPersistedCatalogSymbol() {
        StockCatalogEntity infineon = stock("IFX.DE", "EUR");
        QuoteEntity quote = new QuoteEntity();
        quote.setCurrent(37.45);
        when(stockCatalogRepository.findBySymbolIgnoreCase("IFX")).thenReturn(Optional.empty());
        when(stockCatalogRepository.findBySymbolStartingWithIgnoreCase("IFX.")).thenReturn(List.of(infineon));
        when(quoteRepository.findFirstBySymbolOrderByCreatedDesc("IFX.DE")).thenReturn(Optional.of(quote));

        assertEquals(new BigDecimal("37.45"), service.resolve("IFX", "EUR"));
        verify(yahooFinanceClient, never()).getQuote("IFX.DE");
    }

    @Test
    void loadsLiveYahooQuoteWhenTickerIsNotScanned() {
        when(stockCatalogRepository.findBySymbolIgnoreCase("DUK")).thenReturn(Optional.empty());
        when(stockCatalogRepository.findBySymbolStartingWithIgnoreCase("DUK.")).thenReturn(List.of());
        when(quoteRepository.findFirstBySymbolOrderByCreatedDesc("DUK")).thenReturn(Optional.empty());
        when(yahooFinanceClient.getQuote("DUK")).thenReturn(quote("DUK", 118.25));

        assertEquals(new BigDecimal("118.25"), service.resolve("DUK", "USD"));
    }

    @Test
    void resolvesKnownRevolutAliasFromPersistedMarketQuote() {
        QuoteEntity quote = new QuoteEntity();
        quote.setCurrent(26.75);
        when(quoteRepository.findFirstBySymbolOrderByCreatedDesc("STM")).thenReturn(Optional.of(quote));

        assertEquals(new BigDecimal("26.75"), service.resolve("SGM", "USD"));
        verify(yahooFinanceClient, never()).getQuote("STM");
    }

    @Test
    void resolvesAbjToSwissAbbSymbol() {
        QuoteEntity quote = new QuoteEntity();
        quote.setCurrent(52.10);
        when(quoteRepository.findFirstBySymbolOrderByCreatedDesc("ABBN.SW")).thenReturn(Optional.of(quote));

        assertEquals(new BigDecimal("52.1"), service.resolve("ABJ", "CHF"));
    }

    @Test
    void resolvesXfbToXFabParisSymbol() {
        QuoteEntity quote = new QuoteEntity();
        quote.setCurrent(9.80);
        when(quoteRepository.findFirstBySymbolOrderByCreatedDesc("XFAB.PA")).thenReturn(Optional.of(quote));

        assertEquals(new BigDecimal("9.8"), service.resolve("XFB", "EUR"));
        verify(yahooFinanceClient, never()).getQuote("XFAB.PA");
    }

    private StockCatalogEntity stock(String symbol, String currency) {
        StockCatalogEntity stock = new StockCatalogEntity();
        stock.setSymbol(symbol);
        stock.setCurrency(currency);
        return stock;
    }

    private Quote quote(String symbol, double current) {
        return Quote.builder().symbol(symbol).current(current).build();
    }
}
