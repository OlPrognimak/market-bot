package com.prognimak.marketbot.portfolio.service;

import com.prognimak.marketbot.portfolio.entity.PortfolioIncomeEntity;
import com.prognimak.marketbot.portfolio.entity.PortfolioRealizedLotEntity;
import com.prognimak.marketbot.portfolio.entity.PortfolioTransactionEntity;
import com.prognimak.marketbot.portfolio.model.PortfolioAnalysisResponse;
import com.prognimak.marketbot.portfolio.model.PortfolioProviderType;
import com.prognimak.marketbot.portfolio.model.PortfolioMarkerResponse;
import com.prognimak.marketbot.portfolio.repository.PortfolioIncomeRepository;
import com.prognimak.marketbot.portfolio.repository.PortfolioRealizedLotRepository;
import com.prognimak.marketbot.portfolio.repository.PortfolioTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PortfolioAnalysisService {
    private static final BigDecimal EPSILON = new BigDecimal("0.00000001");
    private static final MathContext MATH_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);
    private final PortfolioTransactionRepository transactionRepository;
    private final PortfolioRealizedLotRepository realizedLotRepository;
    private final PortfolioIncomeRepository incomeRepository;
    private final PortfolioMarketPriceService marketPriceService;

    @Transactional(readOnly = true)
    public PortfolioAnalysisResponse analyze(Long userId) {
        return analyze(userId, null, null, null);
    }

    @Transactional(readOnly = true)
    public PortfolioAnalysisResponse analyze(Long userId, LocalDate from, LocalDate to, String ticker) {
        return analyze(userId, from, to, ticker, null);
    }

    @Transactional(readOnly = true)
    public PortfolioAnalysisResponse analyze(Long userId, LocalDate from, LocalDate to, String ticker, String providerType) {
        validatePeriod(from, to);
        String selectedTicker = normalizeTicker(ticker);
        PortfolioProviderType selectedProvider = normalizeProvider(providerType);
        List<PortfolioTransactionEntity> transactions = transactionRepository.findByUserIdOrderByEventTimeAsc(userId).stream()
                .filter(transaction -> selectedProvider == null || transaction.getProviderType() == selectedProvider)
                .toList();
        Map<PositionKey, PositionState> states = new LinkedHashMap<>();
        for (PortfolioTransactionEntity transaction : transactions) {
            if (transaction.getTicker() == null || transaction.getQuantity() == null) {
                continue;
            }
            PositionKey key = new PositionKey(transaction.getProviderType(), transaction.getTicker(), transaction.getCurrency());
            PositionState state = states.computeIfAbsent(key, ignored -> new PositionState());
            if (transaction.getTransactionType().startsWith("BUY")) {
                state.quantity = state.quantity.add(transaction.getQuantity());
                state.costBasis = state.costBasis.add(transaction.getTotalAmount());
            } else if (transaction.getTransactionType().startsWith("SELL")) {
                if (state.quantity.compareTo(transaction.getQuantity()) < 0) {
                    state.unreconciled = true;
                }
                BigDecimal averageCost = divide(state.costBasis, state.quantity);
                state.costBasis = state.costBasis.subtract(averageCost.multiply(transaction.getQuantity()));
                state.quantity = state.quantity.subtract(transaction.getQuantity());
                if (state.quantity.abs().compareTo(EPSILON) <= 0) {
                    state.quantity = BigDecimal.ZERO;
                    state.costBasis = BigDecimal.ZERO;
                }
            }
        }

        List<PortfolioAnalysisResponse.Position> positions = states.entrySet().stream()
                .filter(entry -> entry.getValue().quantity.compareTo(EPSILON) > 0)
                .filter(entry -> selectedTicker == null || entry.getKey().ticker().equalsIgnoreCase(selectedTicker))
                .map(entry -> toPosition(
                        entry.getKey(),
                        entry.getValue(),
                        marketPriceService.resolve(entry.getKey().ticker(), entry.getKey().currency())
                ))
                .sorted(Comparator.comparing(PortfolioAnalysisResponse.Position::ticker))
                .toList();

        var allLots = realizedLotRepository.findByUserId(userId).stream()
                .filter(lot -> selectedProvider == null || lot.getProviderType() == selectedProvider)
                .toList();
        var allIncomes = incomeRepository.findByUserId(userId).stream()
                .filter(incomeItem -> selectedProvider == null || incomeItem.getProviderType() == selectedProvider)
                .toList();
        var lots = allLots.stream()
                .filter(lot -> inside(lot.getSoldDate(), from, to))
                .filter(lot -> selectedTicker == null || lot.getSymbol().equalsIgnoreCase(selectedTicker))
                .toList();
        var incomes = allIncomes.stream()
                .filter(incomeItem -> inside(incomeItem.getIncomeDate(), from, to))
                .filter(incomeItem -> selectedTicker == null || incomeItem.getSymbol().equalsIgnoreCase(selectedTicker))
                .toList();
        var filteredTransactions = transactions.stream()
                .filter(transaction -> inside(transaction.getEventTime().atZone(ZoneId.systemDefault()).toLocalDate(), from, to))
                .filter(transaction -> selectedTicker == null
                        || selectedTicker.equalsIgnoreCase(transaction.getTicker()))
                .toList();
        Map<String, BigDecimal> profits = new TreeMap<>();
        Map<String, BigDecimal> losses = new TreeMap<>();
        Map<String, BigDecimal> realized = new TreeMap<>();
        lots.forEach(lot -> {
            realized.merge(lot.getCurrency(), lot.getGrossPnl(), BigDecimal::add);
            if (lot.getGrossPnl().signum() >= 0) {
                profits.merge(lot.getCurrency(), lot.getGrossPnl(), BigDecimal::add);
            } else {
                losses.merge(lot.getCurrency(), lot.getGrossPnl(), BigDecimal::add);
            }
        });
        Map<String, BigDecimal> income = new TreeMap<>();
        incomes.forEach(item -> income.merge(item.getCurrency(), item.getNetAmount(), BigDecimal::add));
        List<String> availableTickers = availableTickers(transactions, allLots, allIncomes);
        List<PortfolioAnalysisResponse.FilteredTickerResult> filteredTickerResults =
                filteredTickerResults(filteredTransactions, lots, incomes);
        List<PortfolioAnalysisResponse.RealizedLotDetail> selectedTickerRealizedLots =
                selectedTicker == null ? List.of() : realizedLotDetails(lots);

        return new PortfolioAnalysisResponse(
                selectedProvider,
                filteredTransactions.size(),
                lots.size(),
                incomes.size(),
                positions,
                from,
                to,
                selectedTicker,
                selectedProvider,
                availableTickers,
                filteredTickerResults,
                selectedTickerRealizedLots,
                profits,
                losses,
                realized,
                income
        );
    }

    @Transactional(readOnly = true)
    public PortfolioMarkerResponse markers(Long userId, String ticker) {
        String normalizedTicker = ticker.toUpperCase(Locale.ROOT);
        List<String> candidates = PortfolioTickerAliases.revolutCandidates(normalizedTicker);
        List<PortfolioMarkerResponse.Marker> markers = transactionRepository
                .findByUserIdAndTickerInOrderByEventTimeAsc(userId, candidates).stream()
                .filter(item -> item.getPricePerShare() != null)
                .filter(item -> item.getTransactionType().startsWith("BUY")
                        || item.getTransactionType().startsWith("SELL"))
                .map(item -> new PortfolioMarkerResponse.Marker(
                        item.getId(),
                        item.getEventTime(),
                        item.getTransactionType().startsWith("BUY") ? "BUY" : "SELL",
                        item.getPricePerShare(),
                        item.getQuantity(),
                        item.getTotalAmount(),
                        item.getCurrency()))
                .toList();
        return new PortfolioMarkerResponse(normalizedTicker, PortfolioProviderType.REVOLUT, markers);
    }

    private PortfolioAnalysisResponse.Position toPosition(PositionKey key, PositionState state, BigDecimal currentPrice) {
        BigDecimal averageCost = divide(state.costBasis, state.quantity);
        BigDecimal marketValue = currentPrice == null ? null : state.quantity.multiply(currentPrice);
        BigDecimal unrealized = marketValue == null ? null : marketValue.subtract(state.costBasis);
        BigDecimal percent = unrealized == null || state.costBasis.signum() == 0
                ? null
                : unrealized.multiply(BigDecimal.valueOf(100)).divide(state.costBasis, MATH_CONTEXT);
        return new PortfolioAnalysisResponse.Position(
                key.providerType(),
                key.ticker(),
                key.currency(),
                state.quantity,
                state.costBasis.max(BigDecimal.ZERO),
                averageCost,
                currentPrice,
                marketValue,
                unrealized,
                percent,
                state.unreconciled ? "UNRECONCILED_CORPORATE_ACTION" : "RECONCILED_FROM_COMPLETE_LEDGER"
        );
    }

    private static BigDecimal divide(BigDecimal value, BigDecimal divisor) {
        return divisor == null || divisor.abs().compareTo(EPSILON) <= 0
                ? BigDecimal.ZERO
                : value.divide(divisor, MATH_CONTEXT);
    }

    private List<String> availableTickers(
            List<PortfolioTransactionEntity> transactions,
            List<PortfolioRealizedLotEntity> lots,
            List<PortfolioIncomeEntity> incomes
    ) {
        TreeSet<String> tickers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        transactions.stream().map(PortfolioTransactionEntity::getTicker).filter(Objects::nonNull).forEach(tickers::add);
        lots.stream().map(PortfolioRealizedLotEntity::getSymbol).forEach(tickers::add);
        incomes.stream().map(PortfolioIncomeEntity::getSymbol).forEach(tickers::add);
        return List.copyOf(tickers);
    }

    private List<PortfolioAnalysisResponse.FilteredTickerResult> filteredTickerResults(
            List<PortfolioTransactionEntity> transactions,
            List<PortfolioRealizedLotEntity> lots,
            List<PortfolioIncomeEntity> incomes
    ) {
        Map<ResultKey, ResultState> results = new HashMap<>();
        transactions.stream()
                .filter(transaction -> transaction.getTicker() != null)
                .forEach(transaction -> results
                        .computeIfAbsent(new ResultKey(transaction.getProviderType(), transaction.getTicker(), transaction.getCurrency()),
                                ignored -> new ResultState())
                        .transactionCount++);
        lots.forEach(lot -> {
            ResultState result = results.computeIfAbsent(new ResultKey(lot.getProviderType(), lot.getSymbol(), lot.getCurrency()),
                    ignored -> new ResultState());
            result.realizedLotCount++;
            result.realizedPnl = result.realizedPnl.add(lot.getGrossPnl());
            if (lot.getGrossPnl().signum() >= 0) {
                result.realizedProfit = result.realizedProfit.add(lot.getGrossPnl());
            } else {
                result.realizedLoss = result.realizedLoss.add(lot.getGrossPnl());
            }
        });
        incomes.forEach(income -> {
            ResultState result = results.computeIfAbsent(new ResultKey(income.getProviderType(), income.getSymbol(), income.getCurrency()),
                    ignored -> new ResultState());
            result.incomeCount++;
            result.income = result.income.add(income.getNetAmount());
        });
        return results.entrySet().stream()
                .map(entry -> new PortfolioAnalysisResponse.FilteredTickerResult(
                        entry.getKey().providerType(),
                        entry.getKey().ticker(),
                        entry.getKey().currency(),
                        entry.getValue().transactionCount,
                        entry.getValue().realizedLotCount,
                        entry.getValue().incomeCount,
                        entry.getValue().realizedProfit,
                        entry.getValue().realizedLoss,
                        entry.getValue().realizedPnl,
                        entry.getValue().income))
                .sorted(Comparator.comparing(PortfolioAnalysisResponse.FilteredTickerResult::ticker)
                        .thenComparing(PortfolioAnalysisResponse.FilteredTickerResult::providerType)
                        .thenComparing(PortfolioAnalysisResponse.FilteredTickerResult::currency))
                .toList();
    }

    private List<PortfolioAnalysisResponse.RealizedLotDetail> realizedLotDetails(
            List<PortfolioRealizedLotEntity> lots
    ) {
        return lots.stream()
                .map(lot -> new PortfolioAnalysisResponse.RealizedLotDetail(
                        lot.getProviderType(),
                        lot.getAcquiredDate(),
                        lot.getSoldDate(),
                        lot.getSymbol(),
                        lot.getCurrency(),
                        lot.getQuantity(),
                        lot.getCostBasis(),
                        lot.getGrossProceeds(),
                        lot.getGrossPnl().signum() > 0 ? lot.getGrossPnl() : BigDecimal.ZERO,
                        lot.getGrossPnl().signum() < 0 ? lot.getGrossPnl() : BigDecimal.ZERO,
                        lot.getGrossPnl()))
                .sorted(Comparator.comparing(PortfolioAnalysisResponse.RealizedLotDetail::soldDate)
                        .thenComparing(PortfolioAnalysisResponse.RealizedLotDetail::acquiredDate))
                .toList();
    }

    private boolean inside(LocalDate date, LocalDate from, LocalDate to) {
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Portfolio analysis 'from' date must not be after 'to' date");
        }
    }

    private String normalizeTicker(String ticker) {
        return ticker == null || ticker.isBlank() ? null : ticker.trim().toUpperCase(Locale.ROOT);
    }

    private PortfolioProviderType normalizeProvider(String providerType) {
        if (providerType == null || providerType.isBlank() || "ALL".equalsIgnoreCase(providerType)) {
            return null;
        }
        try {
            return PortfolioProviderType.valueOf(providerType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported portfolio provider type: " + providerType);
        }
    }

    private record PositionKey(PortfolioProviderType providerType, String ticker, String currency) {
    }

    private record ResultKey(PortfolioProviderType providerType, String ticker, String currency) {
    }

    private static final class PositionState {
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal costBasis = BigDecimal.ZERO;
        private boolean unreconciled;
    }

    private static final class ResultState {
        private int transactionCount;
        private int realizedLotCount;
        private int incomeCount;
        private BigDecimal realizedProfit = BigDecimal.ZERO;
        private BigDecimal realizedLoss = BigDecimal.ZERO;
        private BigDecimal realizedPnl = BigDecimal.ZERO;
        private BigDecimal income = BigDecimal.ZERO;
    }
}
