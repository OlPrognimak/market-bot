package com.prognimak.marketbot.portfolio.service;

import com.prognimak.marketbot.portfolio.entity.PortfolioIncomeEntity;
import com.prognimak.marketbot.portfolio.entity.PortfolioRealizedLotEntity;
import com.prognimak.marketbot.portfolio.entity.PortfolioTransactionEntity;
import com.prognimak.marketbot.portfolio.repository.PortfolioIncomeRepository;
import com.prognimak.marketbot.portfolio.repository.PortfolioRealizedLotRepository;
import com.prognimak.marketbot.portfolio.repository.PortfolioTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioAnalysisServiceTest {
    private final PortfolioTransactionRepository transactionRepository = mock(PortfolioTransactionRepository.class);
    private final PortfolioRealizedLotRepository realizedLotRepository = mock(PortfolioRealizedLotRepository.class);
    private final PortfolioIncomeRepository incomeRepository = mock(PortfolioIncomeRepository.class);
    private final PortfolioMarketPriceService marketPriceService = mock(PortfolioMarketPriceService.class);
    private final PortfolioAnalysisService service = new PortfolioAnalysisService(
            transactionRepository, realizedLotRepository, incomeRepository, marketPriceService);

    @Test
    void filtersPeriodResultsButKeepsCompleteCurrentPositionLedger() {
        when(transactionRepository.findByUserIdOrderByEventTimeAsc(1L)).thenReturn(List.of(
                transaction("AAPL", "BUY - MARKET", "2", "200", "2025-01-10T10:00:00Z"),
                transaction("AAPL", "BUY - MARKET", "1", "120", "2026-02-10T10:00:00Z"),
                transaction("MSFT", "BUY - MARKET", "1", "300", "2026-02-10T10:00:00Z")
        ));
        when(realizedLotRepository.findByUserId(1L)).thenReturn(List.of(
                lot("AAPL", "2026-03-01", "100", "USD"),
                lot("AAPL", "2026-03-02", "-25", "USD"),
                lot("MSFT", "2026-03-02", "50", "USD"),
                lot("AAPL", "2025-03-02", "500", "USD")
        ));
        when(incomeRepository.findByUserId(1L)).thenReturn(List.of(
                income("AAPL", "2026-03-03", "12", "USD"),
                income("AAPL", "2025-03-03", "40", "USD")
        ));
        when(marketPriceService.resolve("AAPL", "USD")).thenReturn(new BigDecimal("150"));

        var analysis = service.analyze(
                1L, LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31"), "AAPL");

        assertEquals(0, analysis.transactionCount());
        assertEquals(2, analysis.realizedLotCount());
        assertEquals(1, analysis.incomeCount());
        assertEquals(new BigDecimal("100"), analysis.realizedProfitByCurrency().get("USD"));
        assertEquals(new BigDecimal("-25"), analysis.realizedLossByCurrency().get("USD"));
        assertEquals(new BigDecimal("75"), analysis.realizedPnlByCurrency().get("USD"));
        assertEquals(new BigDecimal("12"), analysis.incomeByCurrency().get("USD"));
        assertEquals(1, analysis.positions().size());
        assertEquals(new BigDecimal("3"), analysis.positions().getFirst().quantity());
        assertEquals(List.of("AAPL", "MSFT"), analysis.availableTickers());
        assertEquals(1, analysis.filteredTickerResults().size());
        var tickerResult = analysis.filteredTickerResults().getFirst();
        assertEquals("AAPL", tickerResult.ticker());
        assertEquals("USD", tickerResult.currency());
        assertEquals(0, tickerResult.transactionCount());
        assertEquals(2, tickerResult.realizedLotCount());
        assertEquals(1, tickerResult.incomeCount());
        assertEquals(new BigDecimal("100"), tickerResult.realizedProfit());
        assertEquals(new BigDecimal("-25"), tickerResult.realizedLoss());
        assertEquals(new BigDecimal("75"), tickerResult.realizedPnl());
        assertEquals(new BigDecimal("12"), tickerResult.income());
        assertEquals(2, analysis.selectedTickerRealizedLots().size());
        assertEquals(new BigDecimal("100"), analysis.selectedTickerRealizedLots().getFirst().realizedProfit());
        assertEquals(BigDecimal.ZERO, analysis.selectedTickerRealizedLots().getFirst().realizedLoss());
        assertEquals(BigDecimal.ZERO, analysis.selectedTickerRealizedLots().getLast().realizedProfit());
        assertEquals(new BigDecimal("-25"), analysis.selectedTickerRealizedLots().getLast().realizedLoss());
    }

    private PortfolioTransactionEntity transaction(String ticker, String type, String quantity, String total, String time) {
        PortfolioTransactionEntity transaction = new PortfolioTransactionEntity();
        transaction.setTicker(ticker);
        transaction.setCurrency("USD");
        transaction.setTransactionType(type);
        transaction.setQuantity(new BigDecimal(quantity));
        transaction.setTotalAmount(new BigDecimal(total));
        transaction.setEventTime(Instant.parse(time));
        return transaction;
    }

    private PortfolioRealizedLotEntity lot(String ticker, String date, String pnl, String currency) {
        PortfolioRealizedLotEntity lot = new PortfolioRealizedLotEntity();
        lot.setSymbol(ticker);
        LocalDate soldDate = LocalDate.parse(date);
        lot.setAcquiredDate(soldDate.minusDays(1));
        lot.setSoldDate(soldDate);
        lot.setQuantity(BigDecimal.ONE);
        lot.setCostBasis(new BigDecimal("100"));
        lot.setGrossProceeds(new BigDecimal("100").add(new BigDecimal(pnl)));
        lot.setGrossPnl(new BigDecimal(pnl));
        lot.setCurrency(currency);
        return lot;
    }

    private PortfolioIncomeEntity income(String ticker, String date, String net, String currency) {
        PortfolioIncomeEntity income = new PortfolioIncomeEntity();
        income.setSymbol(ticker);
        income.setIncomeDate(LocalDate.parse(date));
        income.setNetAmount(new BigDecimal(net));
        income.setCurrency(currency);
        return income;
    }
}
