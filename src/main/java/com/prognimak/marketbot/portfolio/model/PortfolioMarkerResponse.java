package com.prognimak.marketbot.portfolio.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PortfolioMarkerResponse(
        String ticker,
        PortfolioProviderType providerType,
        List<Marker> markers
) {
    public record Marker(
            Long id,
            Instant eventTime,
            String type,
            BigDecimal price,
            BigDecimal quantity,
            BigDecimal totalAmount,
            String currency
    ) {
    }
}
