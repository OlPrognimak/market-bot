package com.prognimak.marketbot.portfolio.service;

import com.prognimak.marketbot.client.YahooFinanceClient;
import com.prognimak.marketbot.entity.QuoteEntity;
import com.prognimak.marketbot.entity.StockCatalogEntity;
import com.prognimak.marketbot.model.Quote;
import com.prognimak.marketbot.repository.QuoteRepository;
import com.prognimak.marketbot.repository.StockCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Resolves a current market price for a portfolio ticker using persisted scanner quotes first and
 * a live Yahoo Finance quote as a fallback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioMarketPriceService {
    private final QuoteRepository quoteRepository;
    private final StockCatalogRepository stockCatalogRepository;
    private final YahooFinanceClient yahooFinanceClient;

    /**
     * Resolves the latest market price for a provider ticker.
     *
     * <p>Catalog symbols sharing the same base ticker are preferred when their configured currency
     * matches the portfolio currency. This resolves provider differences such as {@code IFX} and
     * {@code IFX.DE}.</p>
     *
     * @param ticker portfolio-provider ticker
     * @param currency portfolio position currency
     * @return current price, or {@code null} when no candidate can be resolved
     */
    public BigDecimal resolve(String ticker, String currency) {
        for (String candidate : candidates(ticker, currency)) {
            QuoteEntity persisted = quoteRepository.findFirstBySymbolOrderByCreatedDesc(candidate).orElse(null);
            if (persisted != null && persisted.getCurrent() > 0) {
                return BigDecimal.valueOf(persisted.getCurrent());
            }
        }

        for (String candidate : candidates(ticker, currency)) {
            try {
                Quote quote = yahooFinanceClient.getQuote(candidate);
                if (quote.current() > 0) {
                    return BigDecimal.valueOf(quote.current());
                }
            } catch (RuntimeException exception) {
                log.debug("Could not resolve live portfolio quote for {} using {}", ticker, candidate, exception);
            }
        }
        log.warn("No market price could be resolved for portfolio ticker {} ({})", ticker, currency);
        return null;
    }

    private List<String> candidates(String ticker, String currency) {
        String normalizedTicker = ticker.trim().toUpperCase(Locale.ROOT);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.addAll(PortfolioTickerAliases.marketCandidates(normalizedTicker));
        stockCatalogRepository.findBySymbolIgnoreCase(normalizedTicker)
                .map(StockCatalogEntity::getSymbol)
                .ifPresent(candidates::add);
        stockCatalogRepository.findBySymbolStartingWithIgnoreCase(normalizedTicker + ".").stream()
                .filter(stock -> currency == null || stock.getCurrency() == null
                        || stock.getCurrency().equalsIgnoreCase(currency))
                .map(StockCatalogEntity::getSymbol)
                .filter(Objects::nonNull)
                .forEach(candidates::add);
        candidates.add(normalizedTicker);
        return List.copyOf(candidates);
    }
}
