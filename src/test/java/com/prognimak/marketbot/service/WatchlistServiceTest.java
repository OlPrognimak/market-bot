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
                new DefaultResourceLoader()
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
                new DefaultResourceLoader()
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
                "finnhub-api-key",
                "telegram-bot-token",
                "telegram-chat-id",
                "twelve-data-api-key",
                watchlistFile,
                watchlist,
                -0.4,
                0.4,
                30_000,
                0.8
        );
    }
}
