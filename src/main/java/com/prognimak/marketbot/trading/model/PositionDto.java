package com.prognimak.marketbot.trading.model;

import java.math.BigDecimal;

public record PositionDto(
        String symbol,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        BigDecimal unrealizedPnlPercent,
        String currency,
        BrokerType brokerType
) {
}
