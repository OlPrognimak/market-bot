package com.prognimak.marketbot.portfolio.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PortfolioAnalysisResponse(
        PortfolioProviderType providerType,
        int transactionCount,
        int realizedLotCount,
        int incomeCount,
        List<Position> positions,
        Map<String, BigDecimal> realizedPnlByCurrency,
        Map<String, BigDecimal> incomeByCurrency,
        List<Transaction> recentTransactions
) {
    public record Position(
            String ticker,
            String currency,
            BigDecimal quantity,
            BigDecimal remainingCostBasis,
            BigDecimal averageCost,
            BigDecimal currentPrice,
            BigDecimal marketValue,
            BigDecimal unrealizedPnl,
            BigDecimal unrealizedPnlPercent,
            String reconciliationStatus
    ) {
    }

    public record Transaction(
            Instant eventTime,
            String ticker,
            String transactionType,
            BigDecimal quantity,
            BigDecimal pricePerShare,
            BigDecimal totalAmount,
            String currency
    ) {
    }
}
