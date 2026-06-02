package com.prognimak.marketbot.service;

import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.model.WatchlistItem;
import com.prognimak.marketbot.model.WatchlistPriority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WatchlistServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void watchlistLoadsStructuredItemsAndSkipsDisabledSymbols() throws IOException {
        Path watchlistFile = tempDir.resolve("watchlist.yaml");
        Files.writeString(watchlistFile, """
                watchlist:
                  AAPL:
                    name: Apple
                    region: US
                    sector: Technology
                    exchange: NASDAQ
                    currency: USD
                    priority: HIGH
                    enabled: true
                  ROG.SW:
                    name: Roche Holding AG
                    region: EU
                    sector: Healthcare
                    exchange: SIX
                    currency: CHF
                    priority: LOW
                    enabled: false
                """);

        WatchlistService service = new WatchlistService(
                properties(watchlistFile.toString(), Map.of()),
                new DefaultResourceLoader(),
                emptyStockCatalogService()
        );

        Map<String, WatchlistItem> watchlist = service.watchlist();
        WatchlistItem apple = watchlist.get("AAPL");

        assertAll(
                () -> assertEquals(1, watchlist.size()),
                () -> assertTrue(watchlist.containsKey("AAPL")),
                () -> assertFalse(watchlist.containsKey("ROG.SW")),
                () -> assertEquals("Apple", apple.name()),
                () -> assertEquals("US", apple.region()),
                () -> assertEquals("Technology", apple.sector()),
                () -> assertEquals("NASDAQ", apple.exchange()),
                () -> assertEquals("USD", apple.currency()),
                () -> assertEquals(WatchlistPriority.HIGH, apple.priority())
        );
    }

    @Test
    void watchlistKeepsSimpleSymbolToNameFormatCompatible() throws IOException {
        Path watchlistFile = tempDir.resolve("watchlist.yaml");
        Files.writeString(watchlistFile, """
                watchlist:
                  AAPL: Apple
                """);

        WatchlistService service = new WatchlistService(
                properties(watchlistFile.toString(), Map.of()),
                new DefaultResourceLoader(),
                emptyStockCatalogService()
        );

        WatchlistItem apple = service.watchlist().get("AAPL");

        assertAll(
                () -> assertEquals("AAPL", apple.symbol()),
                () -> assertEquals("Apple", apple.name()),
                () -> assertTrue(apple.enabled()),
                () -> assertEquals(WatchlistPriority.NORMAL, apple.priority())
        );
    }

    private static AppProperties properties(String watchlistFile, Map<String, String> watchlist) {
        return new AppProperties(
                new AppProperties.ProviderConfig("finnhub-api-key", "twelve-data-api-key"),
                new AppProperties.MessageSenderConfig("telegram-bot-token", "telegram-chat-id", 20),
                new AppProperties.ScannerConfig(30_000, 0.0001, 0.08, 3, 1_000),
                new AppProperties.SharesConfig(watchlistFile, watchlist),
                new AppProperties.AlertConfig(-0.4, 0.4, 0.8, 5),
                new AppProperties.CryptoConfig(true, 60_000, null, Map.of("BTC", "Bitcoin"), 1_000_000, 3, "5m", 3, 1_000)
        );
    }

    private static StockCatalogService emptyStockCatalogService() {
        StockCatalogService service = mock(StockCatalogService.class);
        when(service.enabledWatchlist()).thenReturn(Map.of());
        return service;
    }
}
