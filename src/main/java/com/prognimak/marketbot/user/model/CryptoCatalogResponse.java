package com.prognimak.marketbot.user.model;

public record CryptoCatalogResponse(
        Long id,
        String symbol,
        String name,
        String quoteAsset,
        String pairSymbol,
        boolean enabled
) {
}
