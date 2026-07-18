package com.prognimak.marketbot.service;

import com.prognimak.marketbot.client.YahooFinanceClient;
import com.prognimak.marketbot.entity.StockCatalogEntity;
import com.prognimak.marketbot.model.StockSymbolMetadata;
import com.prognimak.marketbot.model.WatchlistItem;
import com.prognimak.marketbot.model.WatchlistPriority;
import com.prognimak.marketbot.portfolio.service.ProviderSymbolMappingService;
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
    private final ProviderSymbolMappingService providerSymbolMappingService;

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
        applyEnriched(entity, request);
        StockCatalogEntity saved = repository.save(entity);
        ensureProviderMappings(saved);
        return toResponse(saved);
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
        applyEnriched(entity, request);
        StockCatalogEntity saved = repository.save(entity);
        ensureProviderMappings(saved);
        return toResponse(saved);
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
            StockCatalogEntity entity = existing.get();
            if (!entity.isEnabled()) {
                entity.setEnabled(true);
                entity = repository.save(entity);
            }
            ensureProviderMappings(entity);
            return entity;
        }

        StockSymbolMetadata metadata = yahooFinanceClient.getStockMetadata(normalized);
        StockCatalogEntity entity = new StockCatalogEntity();
        entity.setSymbol(normalize(metadata.symbol()));
        entity.setName(firstNonBlank(metadata.name(), nameFallback, entity.getSymbol()));
        entity.setRegion(metadata.region());
        entity.setSector(metadata.sector());
        entity.setExchange(metadata.exchange());
        entity.setCurrency(metadata.currency());
        entity.setEnabled(true);
        entity.setPriority(WatchlistPriority.NORMAL);
        StockCatalogEntity saved = repository.save(entity);
        ensureProviderMappings(saved);
        return saved;
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

    private void applyEnriched(StockCatalogEntity entity, CatalogItemRequest request) {
        StockSymbolMetadata metadata = yahooFinanceClient.getStockMetadata(normalize(request.symbol()));
        entity.setSymbol(normalize(metadata.symbol()));
        entity.setName(firstNonBlank(request.name(), metadata.name(), entity.getSymbol()));
        entity.setRegion(firstNonBlank(request.region(), metadata.region()));
        entity.setSector(firstNonBlank(request.sector(), metadata.sector()));
        entity.setExchange(firstNonBlank(request.exchange(), metadata.exchange()));
        entity.setCurrency(firstNonBlank(request.currency(), metadata.currency()));
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setPriority(priority(request.priority()));
    }

    private void ensureProviderMappings(StockCatalogEntity entity) {
        providerSymbolMappingService.ensureTickerMappingsForStock(entity.getSymbol(), entity.getName(), entity.getCurrency());
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
