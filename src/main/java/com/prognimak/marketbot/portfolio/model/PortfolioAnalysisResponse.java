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
        PortfolioProviderType selectedProviderType,
        List<String> availableTickers,
        List<FilteredTickerResult> filteredTickerResults,
        List<RealizedLotDetail> selectedTickerRealizedLots,
        Map<String, BigDecimal> realizedProfitByCurrency,
        Map<String, BigDecimal> realizedLossByCurrency,
        Map<String, BigDecimal> realizedPnlByCurrency,
        Map<String, BigDecimal> incomeByCurrency
) {
    public record Position(
            PortfolioProviderType providerType,
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

    public record FilteredTickerResult(
            PortfolioProviderType providerType,
            String ticker,
            String currency,
            int transactionCount,
            int realizedLotCount,
            int incomeCount,
            BigDecimal realizedProfit,
            BigDecimal realizedLoss,
            BigDecimal realizedPnl,
            BigDecimal income
    ) {
    }

    public record RealizedLotDetail(
            PortfolioProviderType providerType,
            LocalDate acquiredDate,
            LocalDate soldDate,
            String ticker,
            String currency,
            BigDecimal quantity,
            BigDecimal costBasis,
            BigDecimal grossProceeds,
            BigDecimal realizedProfit,
            BigDecimal realizedLoss,
            BigDecimal realizedPnl
    ) {
    }

}
