package com.prognimak.marketbot.news.service;

import com.prognimak.marketbot.entity.CryptoCoinCatalogEntity;
import com.prognimak.marketbot.entity.StockCatalogEntity;
import com.prognimak.marketbot.news.model.InstrumentType;
import com.prognimak.marketbot.repository.CryptoCoinCatalogRepository;
import com.prognimak.marketbot.repository.StockCatalogRepository;
import com.prognimak.marketbot.user.service.UserPropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NewsInstrumentAccessService {
    private final UserPropertyService userPropertyService;
    private final StockCatalogRepository stockCatalogRepository;
    private final CryptoCoinCatalogRepository cryptoCoinCatalogRepository;

    public InstrumentDetails requireAccess(Long userId, InstrumentType type, String requestedSymbol) {
        String symbol = normalize(type, requestedSymbol);
        boolean allowed = switch (type) {
            case SHARE -> userPropertyService.loadUserStockSymbols(userId)
                    .map(symbols -> symbols.contains(symbol))
                    .orElse(true);
            case CRYPTO -> userPropertyService.loadUserCryptoSymbols(userId)
                    .map(symbols -> symbols.contains(symbol))
                    .orElse(true);
        };
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Instrument is not enabled in the user's watchlist");
        }

        return switch (type) {
            case SHARE -> stockCatalogRepository.findBySymbolIgnoreCase(symbol)
                    .map(this::shareDetails)
                    .orElse(new InstrumentDetails(type, symbol, symbol, null, null, null));
            case CRYPTO -> cryptoCoinCatalogRepository.findBySymbolIgnoreCase(symbol)
                    .map(this::cryptoDetails)
                    .orElse(new InstrumentDetails(type, symbol, symbol, null, null, "Binance"));
        };
    }

    private InstrumentDetails shareDetails(StockCatalogEntity entity) {
        return new InstrumentDetails(
                InstrumentType.SHARE,
                entity.getSymbol(),
                entity.getName(),
                entity.getRegion(),
                entity.getSector(),
                entity.getExchange()
        );
    }

    private InstrumentDetails cryptoDetails(CryptoCoinCatalogEntity entity) {
        return new InstrumentDetails(
                InstrumentType.CRYPTO,
                entity.getSymbol(),
                entity.getName(),
                null,
                "Crypto",
                "Binance"
        );
    }

    private String normalize(InstrumentType type, String symbol) {
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        if (type == InstrumentType.CRYPTO && normalized.endsWith("USDT")) {
            return normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    public record InstrumentDetails(
            InstrumentType type,
            String symbol,
            String name,
            String region,
            String sector,
            String exchange
    ) {
    }
}
