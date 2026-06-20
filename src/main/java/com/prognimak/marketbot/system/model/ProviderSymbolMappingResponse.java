package com.prognimak.marketbot.system.model;

import com.prognimak.marketbot.portfolio.model.PortfolioProviderType;

public record ProviderSymbolMappingResponse(
        Long id,
        PortfolioProviderType providerType,
        String sourceSymbol,
        String sourceSymbolType,
        String marketProvider,
        String marketSymbol,
        String instrumentName,
        String currency,
        boolean enabled,
        boolean verified,
        int priority
) {
}
