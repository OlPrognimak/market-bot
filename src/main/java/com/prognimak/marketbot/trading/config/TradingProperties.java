package com.prognimak.marketbot.trading.config;

import com.prognimak.marketbot.trading.model.OrderType;
import com.prognimak.marketbot.trading.model.TradingMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

@ConfigurationProperties(prefix = "trading")
public record TradingProperties(
        boolean enabled,
        TradingMode mode,
        boolean liveEnabled,
        boolean requireManualConfirmation,
        BigDecimal maxOrderValueEur,
        BigDecimal maxDailyLossEur,
        int maxOpenOrdersPerSymbol,
        BigDecimal maxSpreadPercent,
        long previewTtlSeconds,
        boolean allowShortSelling,
        List<OrderType> allowedOrderTypes,
        IbkrProperties ibkr
) {
    public TradingProperties {
        if (mode == null) {
            mode = TradingMode.PAPER;
        }
        if (maxOrderValueEur == null) {
            maxOrderValueEur = BigDecimal.valueOf(500);
        }
        if (maxDailyLossEur == null) {
            maxDailyLossEur = BigDecimal.valueOf(100);
        }
        if (maxOpenOrdersPerSymbol <= 0) {
            maxOpenOrdersPerSymbol = 1;
        }
        if (maxSpreadPercent == null) {
            maxSpreadPercent = BigDecimal.ONE;
        }
        if (previewTtlSeconds <= 0) {
            previewTtlSeconds = 60;
        }
        if (allowedOrderTypes == null || allowedOrderTypes.isEmpty()) {
            allowedOrderTypes = List.of(OrderType.MARKET, OrderType.LIMIT);
        }
        if (ibkr == null) {
            ibkr = new IbkrProperties(false, "127.0.0.1", 7497, 1, "", true, false);
        }
    }

    public record IbkrProperties(
            boolean enabled,
            String host,
            int port,
            int clientId,
            String accountId,
            boolean paperAccountOnly,
            boolean connectOnStartup
    ) {
    }
}
