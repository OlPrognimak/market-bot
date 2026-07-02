package com.prognimak.marketbot.trading.model;

import java.time.Instant;

public record TradeOrderResult(
        Long orderId,
        String brokerOrderId,
        String symbol,
        OrderSide side,
        TradeOrderStatus status,
        Instant submittedAt,
        String message
) {
}
