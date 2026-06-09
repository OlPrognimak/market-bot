package com.prognimak.marketbot.config;

import com.prognimak.marketbot.entity.FuturesCatalogEntity;
import com.prognimak.marketbot.repository.FuturesCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FuturesCatalogInitializer implements ApplicationRunner {
    private final FuturesCatalogRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        List.of(
                new Item("ES", "S&P 500 Futures", "S&P 500", "US", "CME", "USD", "ES=F", "America/Chicago"),
                new Item("NQ", "Nasdaq-100 Futures", "Nasdaq-100", "US", "CME", "USD", "NQ=F", "America/Chicago"),
                new Item("YM", "Dow Futures", "Dow Jones", "US", "CBOT", "USD", "YM=F", "America/Chicago"),
                new Item("RTY", "Russell 2000 Futures", "Russell 2000", "US", "CME", "USD", "RTY=F", "America/Chicago"),
                new Item("DAX", "DAX Futures", "DAX", "EU", "EUREX", "EUR", "FDAX.DE", "Europe/Berlin"),
                new Item("STOXX", "Euro Stoxx 50 Futures", "Euro Stoxx 50", "EU", "EUREX", "EUR", "FESX.DE", "Europe/Berlin")
        ).forEach(this::ensure);
    }

    private void ensure(Item item) {
        repository.findBySymbolIgnoreCase(item.symbol()).orElseGet(() -> {
            FuturesCatalogEntity entity = new FuturesCatalogEntity();
            entity.setSymbol(item.symbol());
            entity.setName(item.name());
            entity.setUnderlying(item.underlying());
            entity.setRegion(item.region());
            entity.setExchange(item.exchange());
            entity.setCurrency(item.currency());
            entity.setProviderSymbol(item.providerSymbol());
            entity.setExchangeTimezone(item.timezone());
            entity.setEnabled(true);
            return repository.save(entity);
        });
    }

    private record Item(String symbol, String name, String underlying, String region, String exchange, String currency,
                        String providerSymbol, String timezone) {}
}
