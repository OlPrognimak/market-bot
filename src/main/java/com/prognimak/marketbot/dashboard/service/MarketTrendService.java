package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.dashboard.model.MarketDirection;
import com.prognimak.marketbot.dashboard.model.TrendProfileInfo;
import com.prognimak.marketbot.config.TrendProperties;
import com.prognimak.marketbot.config.TrendProperties.TrendParameters;
import com.prognimak.marketbot.entity.QuoteEntity;
import com.prognimak.marketbot.model.Quote;
import com.prognimak.marketbot.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Classifies the current short-term direction of a share from recent compatible scanner quotes.
 *
 * <p>The service uses a valid share-specific adaptive profile when available and otherwise uses
 * the configured global fallback parameters.</p>
 */
@Service
@RequiredArgsConstructor
public class MarketTrendService {
    private static final int MINIMUM_POINTS = 3;
    private final QuoteRepository quoteRepository;
    private final TrendProperties properties;
    private final StockTrendProfileService profileService;

    /**
     * Detects the current share trend using persisted quotes and the current provider quote.
     *
     * @param currentQuote latest provider quote
     * @return confirmed upward or downward trend, or neutral when the evidence is insufficient
     */
    public MarketDirection detect(Quote currentQuote) {
        TrendParameters parameters = profileService.parametersFor(currentQuote.symbol());
        List<QuoteEntity> newestFirst = quoteRepository.findBySymbolOrderByCreatedDesc(
                currentQuote.symbol(),
                PageRequest.of(0, properties.effectiveObservationWindow())
        );
        if (newestFirst == null) {
            newestFirst = List.of();
        }

        List<Double> prices = new ArrayList<>();
        newestFirst.stream()
                .takeWhile(entity -> compatibleBaseline(entity, currentQuote))
                .map(QuoteEntity::getCurrent)
                .filter(price -> price > 0)
                .forEach(prices::add);
        Collections.reverse(prices);
        if (currentQuote.current() > 0
                && (prices.isEmpty() || Double.compare(prices.getLast(), currentQuote.current()) != 0)) {
            prices.add(currentQuote.current());
        }
        if (prices.size() > properties.effectiveObservationWindow()) {
            prices = new ArrayList<>(prices.subList(prices.size() - properties.effectiveObservationWindow(), prices.size()));
        }

        return detect(prices, parameters, properties.effectiveShortWindow());
    }

    /**
     * Returns the effective profile currently used by the scanner for the supplied symbol.
     *
     * @param symbol provider share symbol
     * @return adaptive profile details or the global fallback profile details
     */
    public TrendProfileInfo profileInfo(String symbol) {
        TrendParameters parameters = profileService.parametersFor(symbol);
        return new TrendProfileInfo(
                parameters.profileName(),
                parameters.longWeight(),
                parameters.shortWeight(),
                parameters.minimumScorePercent(),
                parameters.confidence()
        );
    }

    MarketDirection detect(List<Double> chronologicalPrices) {
        return detect(chronologicalPrices, properties.defaults(), properties.effectiveShortWindow());
    }

    MarketDirection detect(List<Double> chronologicalPrices, TrendParameters parameters, int shortWindow) {
        if (chronologicalPrices.size() < MINIMUM_POINTS) {
            return MarketDirection.NEUTRAL;
        }

        double minimum = Collections.min(chronologicalPrices);
        double maximum = Collections.max(chronologicalPrices);
        if (minimum <= 0 || maximum == minimum) {
            return MarketDirection.NEUTRAL;
        }

        int minimumIndex = chronologicalPrices.lastIndexOf(minimum);
        int maximumIndex = chronologicalPrices.lastIndexOf(maximum);
        double current = chronologicalPrices.getLast();
        double rangePosition = (current - minimum) / (maximum - minimum);
        double longDelta = percentChange(chronologicalPrices.getFirst(), current);
        int shortStartIndex = Math.max(0, chronologicalPrices.size() - shortWindow);
        double shortDelta = percentChange(chronologicalPrices.get(shortStartIndex), current);
        double trendScore = parameters.longWeight() * longDelta + parameters.shortWeight() * shortDelta;

        if (trendScore >= parameters.minimumScorePercent()
                && shortDelta > 0
                && minimumIndex < maximumIndex
                && rangePosition >= properties.effectiveUpperRangePosition()) {
            return MarketDirection.UP;
        }
        if (trendScore <= -parameters.minimumScorePercent()
                && shortDelta < 0
                && maximumIndex < minimumIndex
                && rangePosition <= properties.effectiveLowerRangePosition()) {
            return MarketDirection.DOWN;
        }
        return MarketDirection.NEUTRAL;
    }

    private boolean compatibleBaseline(QuoteEntity entity, Quote currentQuote) {
        return Math.abs(entity.getPreviousClose() - currentQuote.previousClose()) <= properties.effectivePreviousCloseTolerance();
    }

    private double percentChange(double from, double to) {
        return from <= 0 ? 0 : ((to - from) / from) * 100;
    }
}
