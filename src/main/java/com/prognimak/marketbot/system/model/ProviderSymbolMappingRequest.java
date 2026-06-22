package com.prognimak.marketbot.system.model;

import com.prognimak.marketbot.portfolio.model.PortfolioProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProviderSymbolMappingRequest(
        @NotNull PortfolioProviderType providerType,
        @NotBlank String sourceSymbol,
        @NotBlank String sourceSymbolType,
        @NotBlank String marketProvider,
        @NotBlank String marketSymbol,
        String instrumentName,
        String currency,
        boolean enabled,
        boolean verified,
        int priority
) {
}
