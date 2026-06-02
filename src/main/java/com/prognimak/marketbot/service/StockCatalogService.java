package com.prognimak.marketbot.service;

import com.prognimak.marketbot.client.YahooFinanceClient;
import com.prognimak.marketbot.entity.StockCatalogEntity;
import com.prognimak.marketbot.model.Quote;
import com.prognimak.marketbot.model.WatchlistItem;
import com.prognimak.marketbot.model.WatchlistPriority;
import com.prognimak.marketbot.repository.StockCatalogRepository;
import com.prognimak.marketbot.user.model.CatalogItemRequest;
import com.prognimak.marketbot.user.model.StockCatalogResponse;
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
public class StockCatalogService {
    private final StockCatalogRepository repository;
    private final YahooFinanceClient yahooFinanceClient;

    @Transactional(readOnly = true)
    public List<StockCatalogResponse> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, WatchlistItem> enabledWatchlist() {
        return repository.findByEnabledTrueOrderBySymbolAsc().stream()
                .collect(java.util.LinkedHashMap::new, (map, entity) -> map.put(entity.getSymbol(), toWatchlistItem(entity)), Map::putAll);
    }

    @Transactional
    public StockCatalogResponse create(CatalogItemRequest request) {
        String symbol = normalize(request.symbol());
        if (repository.existsBySymbolIgnoreCase(symbol)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock symbol already exists");
        }
        StockCatalogEntity entity = new StockCatalogEntity();
        apply(entity, request);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public StockCatalogResponse update(Long id, CatalogItemRequest request) {
        StockCatalogEntity entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock catalog item not found"));
        String symbol = normalize(request.symbol());
        repository.findBySymbolIgnoreCase(symbol)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock symbol already exists");
                });
        apply(entity, request);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public StockCatalogEntity ensureProviderSymbol(String symbol, String nameFallback) {
        String normalized = normalize(symbol);
        Optional<StockCatalogEntity> existing = repository.findBySymbolIgnoreCase(normalized);
        if (existing.isPresent()) {
            return existing.get();
        }

        Quote quote = yahooFinanceClient.getQuote(normalized);
        StockCatalogEntity entity = new StockCatalogEntity();
        entity.setSymbol(normalize(quote.symbol()));
        entity.setName(nameFallback == null || nameFallback.isBlank() ? entity.getSymbol() : nameFallback.trim());
        entity.setEnabled(true);
        entity.setPriority(WatchlistPriority.NORMAL);
        return repository.save(entity);
    }

    @Transactional
    public void importInitial(Map<String, WatchlistItem> items) {
        items.values().forEach(item -> repository.findBySymbolIgnoreCase(item.symbol()).orElseGet(() -> {
            StockCatalogEntity entity = new StockCatalogEntity();
            entity.setSymbol(normalize(item.symbol()));
            entity.setName(item.name());
            entity.setRegion(item.region());
            entity.setSector(item.sector());
            entity.setExchange(item.exchange());
            entity.setCurrency(item.currency());
            entity.setEnabled(item.enabled());
            entity.setPriority(item.priority() == null ? WatchlistPriority.NORMAL : item.priority());
            return repository.save(entity);
        }));
    }

    private void apply(StockCatalogEntity entity, CatalogItemRequest request) {
        entity.setSymbol(normalize(request.symbol()));
        entity.setName(request.name().trim());
        entity.setRegion(trimToNull(request.region()));
        entity.setSector(trimToNull(request.sector()));
        entity.setExchange(trimToNull(request.exchange()));
        entity.setCurrency(trimToNull(request.currency()));
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setPriority(priority(request.priority()));
    }

    private WatchlistItem toWatchlistItem(StockCatalogEntity entity) {
        return new WatchlistItem(entity.getSymbol(), entity.getName(), entity.getRegion(), entity.getSector(),
                entity.getExchange(), entity.getCurrency(), entity.isEnabled(), entity.getPriority());
    }

    private StockCatalogResponse toResponse(StockCatalogEntity entity) {
        return new StockCatalogResponse(entity.getId(), entity.getSymbol(), entity.getName(), entity.getRegion(),
                entity.getSector(), entity.getExchange(), entity.getCurrency(), entity.isEnabled(), entity.getPriority().name());
    }

    private WatchlistPriority priority(String value) {
        if (value == null || value.isBlank()) {
            return WatchlistPriority.NORMAL;
        }
        return WatchlistPriority.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
