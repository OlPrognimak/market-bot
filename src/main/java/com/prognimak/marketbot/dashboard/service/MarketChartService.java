package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.dashboard.model.MarketChartPoint;
import com.prognimak.marketbot.dashboard.model.MarketChartRange;
import com.prognimak.marketbot.dashboard.model.MarketChartResponse;
import com.prognimak.marketbot.entity.QuoteEntity;
import com.prognimak.marketbot.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketChartService {
    private final QuoteRepository quoteRepository;

    public MarketChartResponse chart(String symbol, MarketChartRange range) {
        ZoneId zoneId = ZoneId.systemDefault();
        if (range == MarketChartRange.TODAY) {
            return latestActiveTradingDayChart(symbol, zoneId);
        }

        Instant start = range.start(zoneId);
        List<QuoteEntity> entities = quoteRepository.findBySymbolAndCreatedGreaterThanEqualOrderByCreatedAsc(symbol, start);

        return new MarketChartResponse(
                symbol,
                range.name().toLowerCase(),
                start,
                entities.isEmpty() ? null : entities.getFirst().getCreated(),
                entities.isEmpty() ? null : entities.getLast().getCreated(),
                false,
                toPoints(entities)
        );
    }

    private MarketChartResponse latestActiveTradingDayChart(String symbol, ZoneId zoneId) {
        Instant requestedFrom = MarketChartRange.TODAY.start(zoneId);
        QuoteEntity latest = quoteRepository.findFirstBySymbolOrderByCreatedDesc(symbol).orElse(null);
        if (latest == null || latest.getCreated() == null) {
            return new MarketChartResponse(symbol, "today", requestedFrom, null, null, false, List.of());
        }

        LocalDate latestTradingDate = latest.getCreated().atZone(zoneId).toLocalDate();
        Instant start = latestTradingDate.atStartOfDay(zoneId).toInstant();
        Instant end = latestTradingDate.plusDays(1).atStartOfDay(zoneId).toInstant();
        List<QuoteEntity> entities = quoteRepository
                .findBySymbolAndCreatedGreaterThanEqualAndCreatedLessThanOrderByCreatedAsc(symbol, start, end);
        boolean latestTradingDayIsNotToday = latestTradingDate.isBefore(LocalDate.now(zoneId));

        return new MarketChartResponse(
                symbol,
                "today",
                requestedFrom,
                entities.isEmpty() ? null : entities.getFirst().getCreated(),
                entities.isEmpty() ? null : entities.getLast().getCreated(),
                latestTradingDayIsNotToday,
                toPoints(entities)
        );
    }

    private List<MarketChartPoint> toPoints(List<QuoteEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }

        double previousClose = latestPositivePreviousClose(entities);
        double[] previousPrice = {positiveOrFallback(entities.getFirst().getPreviousClose(), entities.getFirst().getCurrent())};

        return entities.stream()
                .map(entity -> {
                    double current = entity.getCurrent();
                    double percentChange = previousClose <= 0 ? 0 : ((current - previousClose) / previousClose) * 100;
                    double delta = previousPrice[0] <= 0 ? 0 : ((current - previousPrice[0]) / previousPrice[0]) * 100;
                    double previous = previousPrice[0];
                    previousPrice[0] = current;
                    return new MarketChartPoint(
                            entity.getCreated(),
                            current,
                            percentChange,
                            delta,
                            entity.getOpen(),
                            entity.getHigh(),
                            entity.getLow(),
                            previous
                    );
                })
                .toList();
    }

    private double latestPositivePreviousClose(List<QuoteEntity> entities) {
        for (int i = entities.size() - 1; i >= 0; i--) {
            double previousClose = entities.get(i).getPreviousClose();
            if (previousClose > 0) {
                return previousClose;
            }
        }
        return entities.getFirst().getCurrent();
    }

    private double positiveOrFallback(double value, double fallback) {
        return value > 0 ? value : fallback;
    }
}
