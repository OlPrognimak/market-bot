package com.prognimak.marketbot.portfolio.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record PortfolioAnalysisResponse(
        PortfolioProviderType providerType,
        int transactionCount,
        int realizedLotCount,
        int incomeCount,
        List<Position> positions,
        LocalDate periodFrom,
        LocalDate periodTo,
        String selectedTicker,
        List<String> availableTickers,
        Map<String, BigDecimal> realizedProfitByCurrency,
        Map<String, BigDecimal> realizedLossByCurrency,
        Map<String, BigDecimal> realizedPnlByCurrency,
        Map<String, BigDecimal> incomeByCurrency
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

}
