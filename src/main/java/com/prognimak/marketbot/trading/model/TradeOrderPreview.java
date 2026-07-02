package com.prognimak.marketbot.trading.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TradeOrderPreview(
        String previewId,
        String symbol,
        OrderSide side,
        OrderType orderType,
        BigDecimal currentPrice,
        BigDecimal estimatedQuantity,
        BigDecimal estimatedValue,
        String currency,
        TradingMode tradingMode,
        boolean allowed,
        List<String> warnings,
        List<String> rejectionReasons,
        String confirmationToken,
        Instant expiresAt
) {
}
