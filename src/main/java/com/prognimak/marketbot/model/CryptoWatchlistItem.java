package com.prognimak.marketbot.model;

public record CryptoWatchlistItem(
        String symbol,
        String name,
        boolean enabled
) {
    public static CryptoWatchlistItem simple(String symbol, String name) {
        return new CryptoWatchlistItem(symbol, name, true);
    }
}
