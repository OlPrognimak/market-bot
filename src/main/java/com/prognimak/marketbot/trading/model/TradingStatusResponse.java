package com.prognimak.marketbot.trading.model;

public record TradingStatusResponse(
        boolean enabled,
        TradingMode mode,
        BrokerType brokerType,
        boolean liveEnabled,
        boolean ibkrEnabled,
        boolean ibkrConnected,
        boolean requireManualConfirmation
) {
}
