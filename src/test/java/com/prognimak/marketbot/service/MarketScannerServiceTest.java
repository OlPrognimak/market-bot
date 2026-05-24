package com.prognimak.marketbot.service;

import com.prognimak.marketbot.client.FinnhubClient;
import com.prognimak.marketbot.client.TelegramClient;
import com.prognimak.marketbot.client.YahooFinanceClient;
import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.dashboard.service.MarketDashboardService;
import com.prognimak.marketbot.entity.QuoteEntity;
import com.prognimak.marketbot.mapper.QuoteMapper;
import com.prognimak.marketbot.model.Quote;
import com.prognimak.marketbot.model.WatchlistItem;
import com.prognimak.marketbot.repository.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketScannerServiceTest {

    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private FinnhubClient finnhubClient;
    @Mock
    private YahooFinanceClient yahooFinanceClient;
    @Mock
    private TelegramClient telegramClient;
    @Mock
    private QuoteMapper quoteMapper;
    @Mock
    private MarketDashboardService marketDashboardService;
    @Mock
    private WatchlistService watchlistService;

    private MarketScannerService service;

    @BeforeEach
    void setUp() {
        service = new MarketScannerService(
                quoteRepository,
                finnhubClient,
                yahooFinanceClient,
                telegramClient,
                properties(0.8, Map.of("AAPL", "Apple")),
                quoteMapper,
                marketDashboardService,
                watchlistService
        );
        when(watchlistService.watchlist()).thenReturn(Map.of("AAPL", WatchlistItem.simple("AAPL", "Apple")));
    }

    @Test
    void scanMarketSavesInitialQuoteWhenNoPersistedHistoryExists() {
        Quote quote = quote("AAPL", 1.25);
        QuoteEntity entity = entity("AAPL", 1.25);

        when(yahooFinanceClient.getQuote("AAPL")).thenReturn(quote);
        when(quoteRepository.findBySymbolAndSendIsFalseOrderByCreatedDesc(eq("AAPL"), any(Pageable.class)))
                .thenReturn(List.of());
        when(quoteRepository.findBySymbolOrderByCreatedDesc(eq("AAPL"), any(Pageable.class)))
                .thenReturn(List.of());
        when(quoteMapper.toEntity(quote)).thenReturn(entity);

        service.scanMarket();

        ArgumentCaptor<QuoteEntity> captor = ArgumentCaptor.forClass(QuoteEntity.class);
        verify(quoteRepository).save(captor.capture());

        assertAll(
                () -> assertEquals(0, captor.getValue().getDelta()),
                () -> assertFalse(captor.getValue().isSend())
        );
        verifyNoInteractions(finnhubClient, telegramClient);
    }

    @Test
    void scanMarketUsesPersistedHistoryOnFirstScanAfterRestart() {
        Quote quote = quote("AAPL", 1.2);
        QuoteEntity entity = entity("AAPL", 1.2);
        QuoteEntity persisted = entity("AAPL", 1.0);

        when(yahooFinanceClient.getQuote("AAPL")).thenReturn(quote);
        when(quoteRepository.findBySymbolAndSendIsFalseOrderByCreatedDesc(eq("AAPL"), any(Pageable.class)))
                .thenReturn(List.of());
        when(quoteRepository.findBySymbolOrderByCreatedDesc(eq("AAPL"), any(Pageable.class)))
                .thenReturn(List.of(persisted));
        when(quoteMapper.toQuotes(List.of(persisted))).thenReturn(new ArrayList<>(List.of(quote("AAPL", 1.0))));
        when(quoteMapper.toEntity(quote)).thenReturn(entity);

        service.scanMarket();

        assertAll(
                () -> assertEquals(0.2, entity.getDelta(), 0.0001),
                () -> assertFalse(entity.isSend())
        );
        verify(quoteRepository).save(entity);
        verifyNoInteractions(finnhubClient, telegramClient);
    }

    @Test
    void scanMarketPersistsDeltaWhenRollingMovementIsBelowThreshold() {
        Quote firstQuote = quote("AAPL", 1.0);
        Quote secondQuote = quote("AAPL", 1.2);
        QuoteEntity firstEntity = entity("AAPL", 1.0);
        QuoteEntity secondEntity = entity("AAPL", 1.2);
        QuoteEntity persisted = entity("AAPL", 1.0);

        when(yahooFinanceClient.getQuote("AAPL")).thenReturn(firstQuote, secondQuote);
        when(quoteMapper.toEntity(firstQuote)).thenReturn(firstEntity);
        when(quoteMapper.toEntity(secondQuote)).thenReturn(secondEntity);
        when(quoteRepository.findBySymbolAndSendIsFalseOrderByCreatedDesc(eq("AAPL"), any(Pageable.class)))
                .thenReturn(List.of(), List.of(persisted));
        when(quoteRepository.findBySymbolOrderByCreatedDesc(eq("AAPL"), any(Pageable.class)))
                .thenReturn(List.of());
        when(quoteMapper.toQuotes(List.of(persisted))).thenReturn(new ArrayList<>(List.of(quote("AAPL", 1.0))));

        service.scanMarket();
        service.scanMarket();

        assertAll(
                () -> assertEquals(0.2, secondEntity.getDelta(), 0.0001),
                () -> assertFalse(secondEntity.isSend()),
                () -> assertFalse(persisted.isSend())
        );
        verify(quoteRepository).save(secondEntity);
        verifyNoInteractions(finnhubClient, telegramClient);
    }

    @Test
    void scanMarketMarksCurrentAndHistoryAsSentWhenRollingMovementExceedsThreshold() {
        Quote firstQuote = quote("AAPL", 1.0);
        Quote secondQuote = quote("AAPL", 1.1);
        QuoteEntity firstEntity = entity("AAPL", 1.0);
        QuoteEntity secondEntity = entity("AAPL", 1.1);
        QuoteEntity latestPersisted = entity("AAPL", 1.0);
        QuoteEntity olderPersisted = entity("AAPL", 0.0);
        List<QuoteEntity> persistedHistoryNewestFirst = List.of(latestPersisted, olderPersisted);

        when(yahooFinanceClient.getQuote("AAPL")).thenReturn(firstQuote, secondQuote);
        when(quoteMapper.toEntity(firstQuote)).thenReturn(firstEntity);
        when(quoteMapper.toEntity(secondQuote)).thenReturn(secondEntity);
        when(quoteRepository.findBySymbolAndSendIsFalseOrderByCreatedDesc(eq("AAPL"), any(Pageable.class)))
                .thenReturn(List.of(), persistedHistoryNewestFirst);
        when(quoteRepository.findBySymbolOrderByCreatedDesc(eq("AAPL"), any(Pageable.class)))
                .thenReturn(List.of());
        when(quoteMapper.toQuotes(persistedHistoryNewestFirst)).thenReturn(new ArrayList<>(List.of(
                quote("AAPL", 1.0),
                quote("AAPL", 0.0)
        )));

        service.scanMarket();
        service.scanMarket();

        assertAll(
                () -> assertEquals(0.1, secondEntity.getDelta(), 0.0001),
                () -> assertTrue(secondEntity.isSend()),
                () -> assertTrue(latestPersisted.isSend()),
                () -> assertTrue(olderPersisted.isSend())
        );
        verify(quoteRepository).save(secondEntity);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());
        String normalizedMessage = messageCaptor.getValue().replace(',', '.');
        assertAll(
                () -> assertNotNull(messageCaptor.getValue()),
                () -> assertTrue(normalizedMessage.contains("Apple (AAPL) UP")),
                () -> assertTrue(normalizedMessage.contains("Day change: 1.10%")),
                () -> assertTrue(normalizedMessage.contains("Move since last check: 0.10%")),
                () -> assertTrue(normalizedMessage.contains("Rolling move 5 checks: 1.10%"))
        );
        verifyNoInteractions(finnhubClient);
    }

    @Test
    void scanMarketMarksOldUnsentHistoryWhenHistoryReachesRollingLimitWithoutAlert() {
        Quote firstQuote = quote("AAPL", 1.0);
        Quote secondQuote = quote("AAPL", 1.1);
        QuoteEntity firstEntity = entity("AAPL", 1.0);
        QuoteEntity secondEntity = entity("AAPL", 1.1);
        List<QuoteEntity> persistedHistory = new ArrayList<>(List.of(
                entity("AAPL", 1.0),
                entity("AAPL", 1.0),
                entity("AAPL", 1.0),
                entity("AAPL", 1.0),
                entity("AAPL", 1.0)
        ));

        when(yahooFinanceClient.getQuote("AAPL")).thenReturn(firstQuote, secondQuote);
        when(quoteMapper.toEntity(firstQuote)).thenReturn(firstEntity);
        when(quoteMapper.toEntity(secondQuote)).thenReturn(secondEntity);
        when(quoteRepository.findBySymbolAndSendIsFalseOrderByCreatedDesc(eq("AAPL"), any(Pageable.class)))
                .thenReturn(List.of(), persistedHistory);
        when(quoteRepository.findBySymbolOrderByCreatedDesc(eq("AAPL"), any(Pageable.class)))
                .thenReturn(List.of());
        when(quoteMapper.toQuotes(persistedHistory)).thenReturn(new ArrayList<>(List.of(
                quote("AAPL", 1.0),
                quote("AAPL", 1.0),
                quote("AAPL", 1.0),
                quote("AAPL", 1.0),
                quote("AAPL", 1.0)
        )));

        service.scanMarket();
        service.scanMarket();

        assertAll(
                () -> assertFalse(secondEntity.isSend()),
                () -> assertTrue(persistedHistory.stream().allMatch(QuoteEntity::isSend))
        );
        verify(quoteRepository).save(secondEntity);
        verify(telegramClient, never()).sendMessage(anyString());
        verifyNoInteractions(finnhubClient, telegramClient);
    }

    @Test
    void scanMarketUsesYahooForEuropeanAndUsSymbols() {
        MarketScannerService multiSymbolService = new MarketScannerService(
                quoteRepository,
                finnhubClient,
                yahooFinanceClient,
                telegramClient,
                properties(0.8, orderedWatchlist()),
                quoteMapper,
                marketDashboardService,
                watchlistService
        );
        when(watchlistService.watchlist()).thenReturn(orderedWatchlistItems());
        Quote usQuote = quote("AAPL", 1.0);
        Quote euQuote = quote("BMW.DE", 1.5);
        QuoteEntity usEntity = entity("AAPL", 1.0);
        QuoteEntity euEntity = entity("BMW.DE", 1.5);

        when(yahooFinanceClient.getQuote("AAPL")).thenReturn(usQuote);
        when(yahooFinanceClient.getQuote("BMW.DE")).thenReturn(euQuote);
        when(quoteMapper.toEntity(usQuote)).thenReturn(usEntity);
        when(quoteMapper.toEntity(euQuote)).thenReturn(euEntity);

        multiSymbolService.scanMarket();

        verify(yahooFinanceClient).getQuote("AAPL");
        verify(yahooFinanceClient).getQuote("BMW.DE");
        verifyNoInteractions(finnhubClient, telegramClient);
    }

    @Test
    void scanMarketHandlesMissingPersistedHistoryWithoutPropagatingException() {
        Quote firstQuote = quote("AAPL", 1.0);
        Quote secondQuote = quote("AAPL", 1.2);
        QuoteEntity firstEntity = entity("AAPL", 1.0);
        QuoteEntity secondEntity = entity("AAPL", 1.2);

        when(yahooFinanceClient.getQuote("AAPL")).thenReturn(firstQuote, secondQuote);
        when(quoteMapper.toEntity(firstQuote)).thenReturn(firstEntity);
        when(quoteMapper.toEntity(secondQuote)).thenReturn(secondEntity);
        when(quoteRepository.findBySymbolAndSendIsFalseOrderByCreatedDesc(eq("AAPL"), any(Pageable.class)))
                .thenReturn(List.of());
        when(quoteRepository.findBySymbolOrderByCreatedDesc(eq("AAPL"), any(Pageable.class)))
                .thenReturn(List.of());

        service.scanMarket();

        assertDoesNotThrow(() -> service.scanMarket());

        verify(quoteRepository).save(secondEntity);
        verifyNoInteractions(finnhubClient, telegramClient);
    }

    private static AppProperties properties(double maximalDeltaPrice, Map<String, String> watchlist) {
        return new AppProperties(
                "finnhub-api-key",
                "telegram-bot-token",
                "telegram-chat-id",
                "twelve-data-api-key",
                null,
                watchlist,
                -0.4,
                0.4,
                30_000,
                maximalDeltaPrice,
                5
        );
    }

    private static Map<String, String> orderedWatchlist() {
        Map<String, String> watchlist = new LinkedHashMap<>();
        watchlist.put("AAPL", "Apple");
        watchlist.put("BMW.DE", "BMW");
        return watchlist;
    }

    private static Map<String, WatchlistItem> orderedWatchlistItems() {
        Map<String, WatchlistItem> watchlist = new LinkedHashMap<>();
        watchlist.put("AAPL", WatchlistItem.simple("AAPL", "Apple"));
        watchlist.put("BMW.DE", WatchlistItem.simple("BMW.DE", "BMW"));
        return watchlist;
    }

    private static Quote quote(String symbol, double percentChange) {
        return Quote.builder()
                .symbol(symbol)
                .current(100.0)
                .change(percentChange)
                .percentChange(percentChange)
                .high(110.0)
                .low(90.0)
                .open(95.0)
                .previousClose(99.0)
                .build();
    }

    private static QuoteEntity entity(String symbol, double percentChange) {
        QuoteEntity entity = new QuoteEntity();
        entity.setSymbol(symbol);
        entity.setCurrent(100.0);
        entity.setChange(percentChange);
        entity.setPercentChange(percentChange);
        entity.setHigh(110.0);
        entity.setLow(90.0);
        entity.setOpen(95.0);
        entity.setPreviousClose(99.0);
        return entity;
    }
}
