package com.prognimak.marketbot.config;

import com.prognimak.marketbot.service.CryptoCoinCatalogService;
import com.prognimak.marketbot.service.CryptoWatchlistService;
import com.prognimak.marketbot.service.StockCatalogService;
import com.prognimak.marketbot.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WatchlistCatalogInitializer implements ApplicationRunner {
    private final WatchlistService watchlistService;
    private final CryptoWatchlistService cryptoWatchlistService;
    private final StockCatalogService stockCatalogService;
    private final CryptoCoinCatalogService cryptoCoinCatalogService;

    @Override
    public void run(ApplicationArguments args) {
        var stocks = watchlistService.configuredWatchlistFromSource();
        stockCatalogService.importInitial(stocks);
        log.info("Imported {} stock catalog symbols from configured watchlist source.", stocks.size());

        var coins = cryptoWatchlistService.configuredWatchlistFromSource();
        cryptoCoinCatalogService.importInitial(coins);
        log.info("Imported {} crypto catalog symbols from configured watchlist source.", coins.size());
    }
}
