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
                entities.stream().map(this::toPoint).toList()
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
                entities.stream().map(this::toPoint).toList()
        );
    }

    private MarketChartPoint toPoint(QuoteEntity entity) {
        return new MarketChartPoint(
                entity.getCreated(),
                entity.getCurrent(),
                entity.getPercentChange(),
                entity.getDelta(),
                entity.getOpen(),
                entity.getHigh(),
                entity.getLow(),
                entity.getPreviousClose()
        );
    }
}
