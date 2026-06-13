package com.prognimak.marketbot.config;

import com.prognimak.marketbot.entity.FuturesCatalogEntity;
import com.prognimak.marketbot.repository.FuturesCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FuturesCatalogInitializer implements ApplicationRunner {
    private final FuturesCatalogRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        List.of(
                new Item("ES", "S&P 500 Futures", "S&P 500", "US", "CME", "USD", "ES=F", "America/Chicago", true),
                new Item("NQ", "Nasdaq-100 Futures", "Nasdaq-100", "US", "CME", "USD", "NQ=F", "America/Chicago", true),
                new Item("YM", "Dow Futures", "Dow Jones", "US", "CBOT", "USD", "YM=F", "America/Chicago", true),
                new Item("RTY", "Russell 2000 Futures", "Russell 2000", "US", "CME", "USD", "RTY=F", "America/Chicago", true),
                // Yahoo exposes the underlying indices, but not these Eurex futures contracts.
                new Item("DAX", "DAX Futures", "DAX", "EU", "EUREX", "EUR", "FDAX.DE", "Europe/Berlin", false),
                new Item("STOXX", "Euro Stoxx 50 Futures", "Euro Stoxx 50", "EU", "EUREX", "EUR", "FESX.DE", "Europe/Berlin", false)
        ).forEach(this::ensure);
    }

    private void ensure(Item item) {
        repository.findBySymbolIgnoreCase(item.symbol()).ifPresentOrElse(entity -> {
            if (!item.enabled() && entity.isEnabled() && item.providerSymbol().equalsIgnoreCase(entity.getProviderSymbol())) {
                entity.setEnabled(false);
                repository.save(entity);
                log.warn("Disabled unsupported Yahoo futures symbol {} ({})", item.symbol(), item.providerSymbol());
            }
        }, () -> {
            FuturesCatalogEntity entity = new FuturesCatalogEntity();
            entity.setSymbol(item.symbol());
            entity.setName(item.name());
            entity.setUnderlying(item.underlying());
            entity.setRegion(item.region());
            entity.setExchange(item.exchange());
            entity.setCurrency(item.currency());
            entity.setProviderSymbol(item.providerSymbol());
            entity.setExchangeTimezone(item.timezone());
            entity.setEnabled(item.enabled());
            repository.save(entity);
        });
    }

    private record Item(String symbol, String name, String underlying, String region, String exchange, String currency,
                        String providerSymbol, String timezone, boolean enabled) {}
}
