package com.prognimak.marketbot.user.api;

import com.prognimak.marketbot.service.CryptoWatchlistService;
import com.prognimak.marketbot.service.SymbolValidationService;
import com.prognimak.marketbot.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/watchlists")
@RequiredArgsConstructor
public class WatchlistCatalogController {
    private final WatchlistService watchlistService;
    private final CryptoWatchlistService cryptoWatchlistService;
    private final SymbolValidationService symbolValidationService;

    @GetMapping("/stocks")
    public List<WatchlistCatalogItem> stocks() {
        return watchlistService.watchlist().values().stream()
                .map(item -> new WatchlistCatalogItem(item.symbol(), item.name()))
                .sorted(Comparator.comparing(WatchlistCatalogItem::symbol, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @GetMapping("/crypto")
    public List<WatchlistCatalogItem> crypto() {
        return cryptoWatchlistService.watchlist().values().stream()
                .map(item -> new WatchlistCatalogItem(item.symbol(), item.name()))
                .sorted(Comparator.comparing(WatchlistCatalogItem::symbol, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @GetMapping("/stocks/validate")
    public SymbolValidationService.SymbolValidationResult validateStock(@RequestParam String symbol) {
        return symbolValidationService.validateStock(symbol);
    }

    @GetMapping("/crypto/validate")
    public SymbolValidationService.SymbolValidationResult validateCrypto(@RequestParam String symbol) {
        return symbolValidationService.validateCrypto(symbol);
    }

    public record WatchlistCatalogItem(String symbol, String name) {
    }
}
