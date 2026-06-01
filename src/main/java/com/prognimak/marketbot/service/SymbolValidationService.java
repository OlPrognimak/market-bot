package com.prognimak.marketbot.service;

import com.prognimak.marketbot.client.BinanceClient;
import com.prognimak.marketbot.client.YahooFinanceClient;
import com.prognimak.marketbot.model.BinanceExchangeInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SymbolValidationService {
    private static final String QUOTE_ASSET = "USDT";

    private final YahooFinanceClient yahooFinanceClient;
    private final BinanceClient binanceClient;

    public SymbolValidationResult validateStock(String symbol) {
        String normalizedSymbol = normalize(symbol);
        if (normalizedSymbol.isBlank()) {
            return SymbolValidationResult.invalid(normalizedSymbol, "Symbol is empty");
        }

        try {
            yahooFinanceClient.getQuote(normalizedSymbol);
            return SymbolValidationResult.valid(normalizedSymbol);
        } catch (Exception e) {
            return SymbolValidationResult.invalid(normalizedSymbol, "Share symbol was not found by Yahoo Finance");
        }
    }

    public SymbolValidationResult validateCrypto(String symbol) {
        String normalizedSymbol = normalize(symbol);
        if (normalizedSymbol.isBlank()) {
            return SymbolValidationResult.invalid(normalizedSymbol, "Coin symbol is empty");
        }

        String pairSymbol = normalizedSymbol.endsWith(QUOTE_ASSET) ? normalizedSymbol : normalizedSymbol + QUOTE_ASSET;
        try {
            BinanceExchangeInfoResponse response = binanceClient.exchangeInfo(pairSymbol);
            boolean valid = response != null
                    && response.symbols() != null
                    && response.symbols().stream().anyMatch(pair ->
                    pairSymbol.equals(pair.symbol())
                            && normalizedSymbol.equals(pair.baseAsset())
                            && QUOTE_ASSET.equals(pair.quoteAsset())
                            && "TRADING".equals(pair.status())
                            && (pair.isSpotTradingAllowed() == null || pair.isSpotTradingAllowed())
            );
            return valid
                    ? SymbolValidationResult.valid(normalizedSymbol)
                    : SymbolValidationResult.invalid(normalizedSymbol, "Crypto coin was not found as active USDT spot pair on Binance");
        } catch (Exception e) {
            return SymbolValidationResult.invalid(normalizedSymbol, "Crypto coin was not found as active USDT spot pair on Binance");
        }
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    public record SymbolValidationResult(
            String symbol,
            boolean valid,
            String message
    ) {
        static SymbolValidationResult valid(String symbol) {
            return new SymbolValidationResult(symbol, true, null);
        }

        static SymbolValidationResult invalid(String symbol, String message) {
            return new SymbolValidationResult(symbol, false, message);
        }
    }
}
