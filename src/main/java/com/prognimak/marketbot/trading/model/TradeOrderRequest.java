package com.prognimak.marketbot.trading.model;

import java.math.BigDecimal;

public record TradeOrderRequest(
        String previewId,
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal amount,
        OrderType orderType,
        BigDecimal limitPrice,
        String currency,
        String exchange,
        BigDecimal stopLossPercent,
        BigDecimal takeProfitPercent,
        String confirmationToken
) {
}
