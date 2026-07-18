package com.prognimak.marketbot.portfolio.service;

import com.prognimak.marketbot.portfolio.entity.ProviderSymbolMappingEntity;
import com.prognimak.marketbot.portfolio.model.PortfolioProviderType;
import com.prognimak.marketbot.portfolio.repository.ProviderSymbolMappingRepository;
import com.prognimak.marketbot.system.model.ProviderSymbolMappingRequest;
import com.prognimak.marketbot.system.model.ProviderSymbolMappingResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProviderSymbolMappingService {
    private static final String DEFAULT_MARKET_PROVIDER = "YAHOO";

    private final ProviderSymbolMappingRepository repository;
    private volatile Map<SourceKey, List<ProviderSymbolMappingEntity>> mappingsBySource = Map.of();
    private volatile Map<String, List<ProviderSymbolMappingEntity>> mappingsByMarketSymbol = Map.of();

    @PostConstruct
    @Transactional
    public void initialize() {
        seedDefaults();
        refreshCache();
    }

    @Transactional(readOnly = true)
    public List<ProviderSymbolMappingResponse> list() {
        return repository.findAllByOrderByProviderTypeAscSourceSymbolAscPriorityAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProviderSymbolMappingResponse save(ProviderSymbolMappingRequest request) {
        ProviderSymbolMappingEntity entity = repository
                .findByProviderTypeAndSourceSymbolIgnoreCaseAndMarketProviderIgnoreCase(
                        request.providerType(), normalize(request.sourceSymbol()), normalize(request.marketProvider()))
                .orElseGet(ProviderSymbolMappingEntity::new);
        apply(entity, request);
        ProviderSymbolMappingResponse response = toResponse(repository.save(entity));
        refreshCache();
        return response;
    }

    @Transactional
    public void ensureTickerMappingsForStock(String marketSymbol, String instrumentName, String currency) {
        String normalizedMarketSymbol = normalize(marketSymbol);
        LinkedHashSet<String> sourceSymbols = new LinkedHashSet<>();
        sourceSymbols.add(baseTicker(normalizedMarketSymbol));
        sourceSymbols.add(normalizedMarketSymbol);
        for (String sourceSymbol : sourceSymbols) {
            ensureTickerMapping(PortfolioProviderType.REVOLUT, sourceSymbol, normalizedMarketSymbol, instrumentName, currency);
            ensureTickerMapping(PortfolioProviderType.TRADE_REPUBLIC, sourceSymbol, normalizedMarketSymbol, instrumentName, currency);
        }
        refreshCache();
    }

    @Transactional
    public ProviderSymbolMappingResponse update(Long id, ProviderSymbolMappingRequest request) {
        ProviderSymbolMappingEntity entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mapping not found"));
        apply(entity, request);
        ProviderSymbolMappingResponse response = toResponse(repository.save(entity));
        refreshCache();
        return response;
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mapping not found");
        }
        repository.deleteById(id);
        refreshCache();
    }

    public String resolveMarketSymbol(PortfolioProviderType providerType, String sourceSymbol) {
        if (sourceSymbol == null || sourceSymbol.isBlank()) {
            return sourceSymbol;
        }
        String normalized = normalize(sourceSymbol);
        return mappingsBySource.getOrDefault(new SourceKey(providerType, normalized), List.of()).stream()
                .findFirst()
                .map(ProviderSymbolMappingEntity::getMarketSymbol)
                .orElseGet(() -> fallbackMarketSymbol(providerType, normalized));
    }

    public List<String> marketCandidates(PortfolioProviderType providerType, String sourceSymbol) {
        if (sourceSymbol == null || sourceSymbol.isBlank()) {
            return List.of();
        }
        String normalized = normalize(sourceSymbol);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        mappingsBySource.getOrDefault(new SourceKey(providerType, normalized), List.of()).stream()
                .map(ProviderSymbolMappingEntity::getMarketSymbol)
                .forEach(candidates::add);
        candidates.add(fallbackMarketSymbol(providerType, normalized));
        return List.copyOf(candidates);
    }

    public List<String> sourceCandidatesForMarketSymbol(String marketSymbol) {
        if (marketSymbol == null || marketSymbol.isBlank()) {
            return List.of();
        }
        String normalized = normalize(marketSymbol);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(normalized);
        candidates.add(baseTicker(normalized));
        mappingsByMarketSymbol.getOrDefault(normalized, List.of()).stream()
                .map(ProviderSymbolMappingEntity::getSourceSymbol)
                .forEach(candidates::add);
        candidates.addAll(PortfolioTickerAliases.revolutCandidates(normalized));
        return List.copyOf(candidates);
    }

    @Transactional
    public void refreshCache() {
        List<ProviderSymbolMappingEntity> rows = repository.findByEnabledTrueOrderByProviderTypeAscSourceSymbolAscPriorityAsc();
        mappingsBySource = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> new SourceKey(row.getProviderType(), normalize(row.getSourceSymbol())),
                        LinkedHashMap::new,
                        Collectors.toList()));
        mappingsByMarketSymbol = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> normalize(row.getMarketSymbol()),
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private void seedDefaults() {
        defaultMappings().forEach(defaultMapping -> {
            if (!repository.existsByProviderTypeAndSourceSymbolIgnoreCaseAndMarketProviderIgnoreCase(
                    defaultMapping.providerType(), defaultMapping.sourceSymbol(), DEFAULT_MARKET_PROVIDER)) {
                ProviderSymbolMappingEntity entity = new ProviderSymbolMappingEntity();
                entity.setProviderType(defaultMapping.providerType());
                entity.setSourceSymbol(defaultMapping.sourceSymbol());
                entity.setSourceSymbolType(defaultMapping.sourceSymbolType());
                entity.setMarketProvider(DEFAULT_MARKET_PROVIDER);
                entity.setMarketSymbol(defaultMapping.marketSymbol());
                entity.setInstrumentName(defaultMapping.instrumentName());
                entity.setEnabled(true);
                entity.setVerified(true);
                repository.save(entity);
            }
        });
    }

    private void ensureTickerMapping(
            PortfolioProviderType providerType,
            String sourceSymbol,
            String marketSymbol,
            String instrumentName,
            String currency
    ) {
        ProviderSymbolMappingEntity entity = repository
                .findByProviderTypeAndSourceSymbolIgnoreCaseAndMarketProviderIgnoreCase(
                        providerType, sourceSymbol, DEFAULT_MARKET_PROVIDER)
                .orElseGet(ProviderSymbolMappingEntity::new);
        if (entity.getId() != null && entity.getMarketSymbol() != null && !normalize(entity.getMarketSymbol()).equals(marketSymbol)) {
            return;
        }
        entity.setProviderType(providerType);
        entity.setSourceSymbol(sourceSymbol);
        entity.setSourceSymbolType("TICKER");
        entity.setMarketProvider(DEFAULT_MARKET_PROVIDER);
        entity.setMarketSymbol(marketSymbol);
        entity.setInstrumentName(blankToNull(instrumentName));
        entity.setCurrency(blankToNull(currency));
        entity.setEnabled(true);
        entity.setVerified(true);
        if (entity.getPriority() <= 0) {
            entity.setPriority(100);
        }
        repository.save(entity);
    }

    private List<DefaultMapping> defaultMappings() {
        return List.of(
                new DefaultMapping(PortfolioProviderType.REVOLUT, "ABJ", "TICKER", "ABBN.SW", "ABB"),
                new DefaultMapping(PortfolioProviderType.REVOLUT, "ASME", "TICKER", "ASML", "ASML"),
                new DefaultMapping(PortfolioProviderType.REVOLUT, "SGM", "TICKER", "STM", "STMicroelectronics"),
                new DefaultMapping(PortfolioProviderType.REVOLUT, "IRBTQ", "TICKER", "IRBT", "iRobot"),
                new DefaultMapping(PortfolioProviderType.REVOLUT, "AIR1", "TICKER", "AIR.PA", "Airbus"),
                new DefaultMapping(PortfolioProviderType.REVOLUT, "ENR1", "TICKER", "ENR.DE", "Siemens Energy"),
                new DefaultMapping(PortfolioProviderType.REVOLUT, "SEJ1", "TICKER", "SAF.PA", "Safran"),
                new DefaultMapping(PortfolioProviderType.REVOLUT, "XFB", "TICKER", "XFAB.PA", "X-FAB"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "US0378331005", "ISIN", "AAPL", "Apple"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "US5951121038", "ISIN", "MU", "Micron"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "FR0000121972", "ISIN", "SU.PA", "Schneider Electric"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "NL0000235190", "ISIN", "AIR.PA", "Airbus"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "US20825C1045", "ISIN", "COP", "ConocoPhillips"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "US5738741041", "ISIN", "MRVL", "Marvell"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "US76206K1079", "ISIN", "RNMBY", "Rheinmetall"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "US82621A2033", "ISIN", "ENR.DE", "Siemens Energy"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "US0727303028", "ISIN", "BAYRY", "Bayer"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "US11135F1012", "ISIN", "AVGO", "Broadcom"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "US1667641005", "ISIN", "CVX", "Chevron"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "US67066G1040", "ISIN", "NVDA", "NVIDIA"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "DE0007236101", "ISIN", "SIE.DE", "Siemens"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "DE0006599905", "ISIN", "MRK.DE", "Merck KGaA"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "GB0002634946", "ISIN", "BSP.DE", "BAE Systems"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "CA53680V1076", "ISIN", "LSPD.TO", "Lightspeed"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "US0258161092", "ISIN", "AXP", "American Express"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "US0605051046", "ISIN", "BAC", "Bank of America"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "US46625H1005", "ISIN", "JPM", "JPMorgan Chase"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "US7069151055", "ISIN", "PENG", "Penguin Solutions"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "US7509171069", "ISIN", "RMBS", "Rambus"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "US84615Q1031", "ISIN", "SPCX", "SpaceX"),
                new DefaultMapping(PortfolioProviderType.TRADE_REPUBLIC, "IE0000ZL1RD2", "ISIN", "C8PX.DE", "AI Semiconductor & Quantum")
        );
    }

    private void apply(ProviderSymbolMappingEntity entity, ProviderSymbolMappingRequest request) {
        entity.setProviderType(request.providerType());
        entity.setSourceSymbol(normalize(request.sourceSymbol()));
        entity.setSourceSymbolType(normalize(request.sourceSymbolType()));
        entity.setMarketProvider(normalize(request.marketProvider()));
        entity.setMarketSymbol(normalize(request.marketSymbol()));
        entity.setInstrumentName(blankToNull(request.instrumentName()));
        entity.setCurrency(blankToNull(request.currency()));
        entity.setEnabled(request.enabled());
        entity.setVerified(request.verified());
        entity.setPriority(request.priority());
    }

    private ProviderSymbolMappingResponse toResponse(ProviderSymbolMappingEntity entity) {
        return new ProviderSymbolMappingResponse(
                entity.getId(),
                entity.getProviderType(),
                entity.getSourceSymbol(),
                entity.getSourceSymbolType(),
                entity.getMarketProvider(),
                entity.getMarketSymbol(),
                entity.getInstrumentName(),
                entity.getCurrency(),
                entity.isEnabled(),
                entity.isVerified(),
                entity.getPriority()
        );
    }

    private String fallbackMarketSymbol(PortfolioProviderType providerType, String normalized) {
        if (providerType == PortfolioProviderType.REVOLUT) {
            return PortfolioTickerAliases.revolutMarketSymbol(normalized);
        }
        if (providerType == PortfolioProviderType.TRADE_REPUBLIC) {
            return PortfolioTickerAliases.tradeRepublicMarketSymbol(normalized);
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String baseTicker(String ticker) {
        int suffixIndex = ticker.indexOf('.');
        return suffixIndex < 0 ? ticker : ticker.substring(0, suffixIndex);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record SourceKey(PortfolioProviderType providerType, String sourceSymbol) {
    }

    private record DefaultMapping(
            PortfolioProviderType providerType,
            String sourceSymbol,
            String sourceSymbolType,
            String marketSymbol,
            String instrumentName
    ) {
    }
}
