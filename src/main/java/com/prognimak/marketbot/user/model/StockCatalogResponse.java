package com.prognimak.marketbot.user.model;

public record StockCatalogResponse(
        Long id,
        String symbol,
        String name,
        String region,
        String sector,
        String exchange,
        String currency,
        boolean enabled,
        String priority
) {
}
