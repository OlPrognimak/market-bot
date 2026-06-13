package com.prognimak.marketbot.portfolio.service;

import com.prognimak.marketbot.entity.QuoteEntity;
import com.prognimak.marketbot.portfolio.entity.PortfolioTransactionEntity;
import com.prognimak.marketbot.portfolio.model.PortfolioAnalysisResponse;
import com.prognimak.marketbot.portfolio.model.PortfolioProviderType;
import com.prognimak.marketbot.portfolio.model.PortfolioMarkerResponse;
import com.prognimak.marketbot.portfolio.repository.PortfolioIncomeRepository;
import com.prognimak.marketbot.portfolio.repository.PortfolioRealizedLotRepository;
import com.prognimak.marketbot.portfolio.repository.PortfolioTransactionRepository;
import com.prognimak.marketbot.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioAnalysisService {
    private static final BigDecimal EPSILON = new BigDecimal("0.00000001");
    private static final MathContext MATH_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);
    private static final Map<String, List<String>> REVOLUT_TICKER_ALIASES = Map.of(
            "ASML", List.of("ASME"),
            "STM", List.of("SGM"),
            "IRBT", List.of("IRBTQ"),
            "AIR", List.of("AIR1"),
            "ENR", List.of("ENR1")
    );

    private final PortfolioTransactionRepository transactionRepository;
    private final PortfolioRealizedLotRepository realizedLotRepository;
    private final PortfolioIncomeRepository incomeRepository;
    private final QuoteRepository quoteRepository;

    @Transactional(readOnly = true)
    public PortfolioAnalysisResponse analyze(Long userId) {
        List<PortfolioTransactionEntity> transactions = transactionRepository.findByUserIdOrderByEventTimeAsc(userId);
        Map<PositionKey, PositionState> states = new LinkedHashMap<>();
        for (PortfolioTransactionEntity transaction : transactions) {
            if (transaction.getTicker() == null || transaction.getQuantity() == null) {
                continue;
            }
            PositionKey key = new PositionKey(transaction.getTicker(), transaction.getCurrency());
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

        Map<String, QuoteEntity> quotes = states.keySet().stream()
                .map(PositionKey::ticker)
                .distinct()
                .map(symbol -> quoteRepository.findFirstBySymbolOrderByCreatedDesc(symbol).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(QuoteEntity::getSymbol, Function.identity(), (left, right) -> left));

        List<PortfolioAnalysisResponse.Position> positions = states.entrySet().stream()
                .filter(entry -> entry.getValue().quantity.compareTo(EPSILON) > 0)
                .map(entry -> toPosition(entry.getKey(), entry.getValue(), quotes.get(entry.getKey().ticker())))
                .sorted(Comparator.comparing(PortfolioAnalysisResponse.Position::ticker))
                .toList();

        var lots = realizedLotRepository.findByUserId(userId);
        var incomes = incomeRepository.findByUserId(userId);
        Map<String, BigDecimal> realized = new TreeMap<>();
        lots.forEach(lot -> realized.merge(lot.getCurrency(), lot.getGrossPnl(), BigDecimal::add));
        Map<String, BigDecimal> income = new TreeMap<>();
        incomes.forEach(item -> income.merge(item.getCurrency(), item.getNetAmount(), BigDecimal::add));

        List<PortfolioAnalysisResponse.Transaction> recent = transactionRepository
                .findTop100ByUserIdOrderByEventTimeDesc(userId).stream()
                .map(item -> new PortfolioAnalysisResponse.Transaction(
                        item.getEventTime(),
                        item.getTicker(),
                        item.getTransactionType(),
                        item.getQuantity(),
                        item.getPricePerShare(),
                        item.getTotalAmount(),
                        item.getCurrency()))
                .toList();

        return new PortfolioAnalysisResponse(
                PortfolioProviderType.REVOLUT,
                transactions.size(),
                lots.size(),
                incomes.size(),
                positions,
                realized,
                income,
                recent
        );
    }

    @Transactional(readOnly = true)
    public PortfolioMarkerResponse markers(Long userId, String ticker) {
        String normalizedTicker = ticker.toUpperCase(Locale.ROOT);
        String baseTicker = normalizedTicker.contains(".")
                ? normalizedTicker.substring(0, normalizedTicker.indexOf('.'))
                : normalizedTicker;
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(normalizedTicker);
        candidates.add(baseTicker);
        candidates.addAll(REVOLUT_TICKER_ALIASES.getOrDefault(baseTicker, List.of()));
        List<PortfolioMarkerResponse.Marker> markers = transactionRepository
                .findByUserIdAndTickerInOrderByEventTimeAsc(userId, List.copyOf(candidates)).stream()
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

    private PortfolioAnalysisResponse.Position toPosition(PositionKey key, PositionState state, QuoteEntity quote) {
        BigDecimal averageCost = divide(state.costBasis, state.quantity);
        BigDecimal currentPrice = quote == null ? null : BigDecimal.valueOf(quote.getCurrent());
        BigDecimal marketValue = currentPrice == null ? null : state.quantity.multiply(currentPrice);
        BigDecimal unrealized = marketValue == null ? null : marketValue.subtract(state.costBasis);
        BigDecimal percent = unrealized == null || state.costBasis.signum() == 0
                ? null
                : unrealized.multiply(BigDecimal.valueOf(100)).divide(state.costBasis, MATH_CONTEXT);
        return new PortfolioAnalysisResponse.Position(
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

    private record PositionKey(String ticker, String currency) {
    }

    private static final class PositionState {
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal costBasis = BigDecimal.ZERO;
        private boolean unreconciled;
    }
}
