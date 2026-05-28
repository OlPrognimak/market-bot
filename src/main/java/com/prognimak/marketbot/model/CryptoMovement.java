package com.prognimak.marketbot.model;

public record CryptoMovement(
        String symbol,
        String baseAsset,
        String name,
        String window,
        double openPrice,
        double closePrice,
        double priceChangePercent,
        double quoteVolume
) {
}
