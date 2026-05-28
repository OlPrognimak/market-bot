package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.dashboard.model.MarketChartPoint;
import com.prognimak.marketbot.dashboard.model.MarketChartRange;
import com.prognimak.marketbot.dashboard.model.MarketChartResponse;
import com.prognimak.marketbot.entity.CryptoQuoteEntity;
import com.prognimak.marketbot.repository.CryptoQuoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CryptoChartService {
    private static final String QUOTE_ASSET = "USDT";

    private final CryptoQuoteRepository cryptoQuoteRepository;

    public MarketChartResponse chart(String symbol, MarketChartRange range) {
        String pairSymbol = normalizePair(symbol);
        ZoneId zoneId = ZoneId.systemDefault();
        if (range == MarketChartRange.TODAY) {
            return latestActiveDayChart(pairSymbol, zoneId);
        }
        if (range == MarketChartRange.YESTERDAY) {
            return dayChart(pairSymbol, LocalDate.now(zoneId).minusDays(1), range, zoneId);
        }

        Instant requestedFrom = range.start(zoneId);
        List<CryptoQuoteEntity> quotes = cryptoQuoteRepository.findBySymbolAndCreatedGreaterThanEqualOrderByCreatedAsc(
                pairSymbol,
                requestedFrom
        );
        List<MarketChartPoint> points = toPoints(quotes);

        return new MarketChartResponse(
                pairSymbol,
                range.name().toLowerCase(Locale.ROOT),
                requestedFrom,
                points.isEmpty() ? null : points.getFirst().time(),
                points.isEmpty() ? null : points.getLast().time(),
                false,
                points
        );
    }

    private MarketChartResponse latestActiveDayChart(String pairSymbol, ZoneId zoneId) {
        Instant requestedFrom = MarketChartRange.TODAY.start(zoneId);
        CryptoQuoteEntity latest = cryptoQuoteRepository.findFirstBySymbolOrderByCreatedDesc(pairSymbol).orElse(null);
        if (latest == null || latest.getCreated() == null) {
            return new MarketChartResponse(pairSymbol, "today", requestedFrom, null, null, false, List.of());
        }

        LocalDate latestDate = latest.getCreated().atZone(zoneId).toLocalDate();
        MarketChartResponse response = dayChart(pairSymbol, latestDate, MarketChartRange.TODAY, zoneId);
        boolean latestDayIsNotToday = latestDate.isBefore(LocalDate.now(zoneId));
        return new MarketChartResponse(
                pairSymbol,
                "today",
                requestedFrom,
                response.actualFrom(),
                response.actualTo(),
                latestDayIsNotToday,
                response.points()
        );
    }

    private MarketChartResponse dayChart(String pairSymbol, LocalDate day, MarketChartRange range, ZoneId zoneId) {
        Instant start = day.atStartOfDay(zoneId).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(zoneId).toInstant();
        List<CryptoQuoteEntity> quotes = cryptoQuoteRepository
                .findBySymbolAndCreatedGreaterThanEqualAndCreatedLessThanOrderByCreatedAsc(pairSymbol, start, end);
        List<MarketChartPoint> points = toPoints(quotes);

        return new MarketChartResponse(
                pairSymbol,
                range.name().toLowerCase(Locale.ROOT),
                start,
                points.isEmpty() ? null : points.getFirst().time(),
                points.isEmpty() ? null : points.getLast().time(),
                false,
                points
        );
    }

    private String normalizePair(String symbol) {
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        return normalized.endsWith(QUOTE_ASSET) ? normalized : normalized + QUOTE_ASSET;
    }

    private List<MarketChartPoint> toPoints(List<CryptoQuoteEntity> quotes) {
        if (quotes.isEmpty()) {
            return List.of();
        }

        double basePrice = positiveOrFallback(quotes.getFirst().getOpenPrice(), quotes.getFirst().getClosePrice());
        double[] previousClose = {basePrice};

        return quotes.stream()
                .map(entity -> {
                    double close = entity.getClosePrice();
                    double percentChange = basePrice <= 0 ? 0 : ((close - basePrice) / basePrice) * 100;
                    double delta = previousClose[0] <= 0 ? 0 : ((close - previousClose[0]) / previousClose[0]) * 100;
                    double previous = previousClose[0];
                    previousClose[0] = close;

                    return new MarketChartPoint(
                            entity.getCreated(),
                            close,
                            percentChange,
                            delta,
                            entity.getOpenPrice(),
                            entity.getHighPrice(),
                            entity.getLowPrice(),
                            previous
                    );
                })
                .toList();
    }

    private double positiveOrFallback(double value, double fallback) {
        return value > 0 ? value : fallback;
    }
}
