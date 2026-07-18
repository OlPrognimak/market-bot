package com.prognimak.marketbot.model;

public record StockSymbolMetadata(
        String symbol,
        String name,
        String region,
        String sector,
        String exchange,
        String currency
) {
}
