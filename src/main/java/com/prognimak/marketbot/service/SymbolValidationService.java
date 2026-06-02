package com.prognimak.marketbot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SymbolValidationService {
    private final StockCatalogService stockCatalogService;
    private final CryptoCoinCatalogService cryptoCoinCatalogService;

    public SymbolValidationResult validateStock(String symbol) {
        String normalizedSymbol = normalize(symbol);
        if (normalizedSymbol.isBlank()) {
            return SymbolValidationResult.invalid(normalizedSymbol, "Symbol is empty");
        }

        try {
            stockCatalogService.ensureProviderSymbol(normalizedSymbol, normalizedSymbol);
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

        try {
            cryptoCoinCatalogService.ensureProviderSymbol(normalizedSymbol, normalizedSymbol);
            return SymbolValidationResult.valid(normalizedSymbol);
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
