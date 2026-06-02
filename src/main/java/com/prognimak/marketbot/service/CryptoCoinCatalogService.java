package com.prognimak.marketbot.service;

import com.prognimak.marketbot.client.BinanceClient;
import com.prognimak.marketbot.entity.CryptoCoinCatalogEntity;
import com.prognimak.marketbot.model.BinanceExchangeInfoResponse;
import com.prognimak.marketbot.model.CryptoWatchlistItem;
import com.prognimak.marketbot.repository.CryptoCoinCatalogRepository;
import com.prognimak.marketbot.user.model.CatalogItemRequest;
import com.prognimak.marketbot.user.model.CryptoCatalogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CryptoCoinCatalogService {
    private static final String QUOTE_ASSET = "USDT";

    private final CryptoCoinCatalogRepository repository;
    private final BinanceClient binanceClient;

    @Transactional(readOnly = true)
    public List<CryptoCatalogResponse> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, CryptoWatchlistItem> enabledWatchlist() {
        return repository.findByEnabledTrueOrderBySymbolAsc().stream()
                .collect(java.util.LinkedHashMap::new, (map, entity) -> map.put(entity.getSymbol(), toWatchlistItem(entity)), Map::putAll);
    }

    @Transactional
    public CryptoCatalogResponse create(CatalogItemRequest request) {
        String symbol = normalize(request.symbol());
        if (repository.existsBySymbolIgnoreCase(symbol)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Crypto coin already exists");
        }
        CryptoCoinCatalogEntity entity = new CryptoCoinCatalogEntity();
        apply(entity, request);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public CryptoCatalogResponse update(Long id, CatalogItemRequest request) {
        CryptoCoinCatalogEntity entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Crypto catalog item not found"));
        String symbol = normalize(request.symbol());
        repository.findBySymbolIgnoreCase(symbol)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Crypto coin already exists");
                });
        apply(entity, request);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public CryptoCoinCatalogEntity ensureProviderSymbol(String symbol, String nameFallback) {
        String normalized = normalize(symbol);
        Optional<CryptoCoinCatalogEntity> existing = repository.findBySymbolIgnoreCase(normalized);
        if (existing.isPresent()) {
            return existing.get();
        }

        BinanceExchangeInfoResponse.SymbolInfo pair = activePair(normalized);
        CryptoCoinCatalogEntity entity = new CryptoCoinCatalogEntity();
        entity.setSymbol(normalize(pair.baseAsset()));
        entity.setName(nameFallback == null || nameFallback.isBlank() ? entity.getSymbol() : nameFallback.trim());
        entity.setQuoteAsset(QUOTE_ASSET);
        entity.setPairSymbol(pair.symbol());
        entity.setEnabled(true);
        return repository.save(entity);
    }

    @Transactional
    public void importInitial(Map<String, CryptoWatchlistItem> items) {
        items.values().forEach(item -> repository.findBySymbolIgnoreCase(item.symbol()).orElseGet(() -> {
            CryptoCoinCatalogEntity entity = new CryptoCoinCatalogEntity();
            entity.setSymbol(normalize(item.symbol()));
            entity.setName(item.name());
            entity.setQuoteAsset(QUOTE_ASSET);
            entity.setPairSymbol(normalize(item.symbol()) + QUOTE_ASSET);
            entity.setEnabled(item.enabled());
            return repository.save(entity);
        }));
    }

    private void apply(CryptoCoinCatalogEntity entity, CatalogItemRequest request) {
        String symbol = normalize(request.symbol());
        entity.setSymbol(symbol);
        entity.setName(request.name().trim());
        entity.setQuoteAsset(QUOTE_ASSET);
        entity.setPairSymbol(symbol + QUOTE_ASSET);
        entity.setEnabled(request.enabled() == null || request.enabled());
    }

    private BinanceExchangeInfoResponse.SymbolInfo activePair(String symbol) {
        String pairSymbol = symbol.endsWith(QUOTE_ASSET) ? symbol : symbol + QUOTE_ASSET;
        BinanceExchangeInfoResponse response = binanceClient.exchangeInfo(pairSymbol);
        if (response == null || response.symbols() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Crypto coin was not found as active USDT spot pair on Binance");
        }
        return response.symbols().stream()
                .filter(pair -> pairSymbol.equals(pair.symbol()))
                .filter(pair -> QUOTE_ASSET.equals(pair.quoteAsset()))
                .filter(pair -> "TRADING".equals(pair.status()))
                .filter(pair -> pair.isSpotTradingAllowed() == null || pair.isSpotTradingAllowed())
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Crypto coin was not found as active USDT spot pair on Binance"));
    }

    private CryptoWatchlistItem toWatchlistItem(CryptoCoinCatalogEntity entity) {
        return new CryptoWatchlistItem(entity.getSymbol(), entity.getName(), entity.isEnabled());
    }

    private CryptoCatalogResponse toResponse(CryptoCoinCatalogEntity entity) {
        return new CryptoCatalogResponse(entity.getId(), entity.getSymbol(), entity.getName(),
                entity.getQuoteAsset(), entity.getPairSymbol(), entity.isEnabled());
    }

    private String normalize(String symbol) {
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        return normalized.endsWith(QUOTE_ASSET) ? normalized.substring(0, normalized.length() - QUOTE_ASSET.length()) : normalized;
    }
}
