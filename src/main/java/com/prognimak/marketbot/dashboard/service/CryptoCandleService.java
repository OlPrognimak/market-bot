package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.client.BinanceClient;
import com.prognimak.marketbot.dashboard.model.MarketCandlePoint;
import com.prognimak.marketbot.dashboard.model.MarketCandleResponse;
import com.prognimak.marketbot.dashboard.model.MarketChartRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CryptoCandleService {
    private static final String QUOTE_ASSET = "USDT";

    private final BinanceClient binanceClient;

    public MarketCandleResponse candles(String symbol, MarketChartRange range) {
        String pair = normalizePair(symbol);
        CandleRequest request = CandleRequest.forRange(range);
        ZoneId zoneId = ZoneId.systemDefault();
        Instant requestedFrom = range.start(zoneId);
        List<List<Object>> rawCandles = binanceClient.klines(
                pair,
                request.interval(),
                requestedFrom.toEpochMilli(),
                request.limit()
        );
        List<MarketCandlePoint> points = mapCandles(rawCandles);
        if (range == MarketChartRange.TODAY || range == MarketChartRange.YESTERDAY) {
            LocalDate requestedDate = requestedFrom.atZone(zoneId).toLocalDate();
            points = points.stream()
                    .filter(point -> point.time().atZone(zoneId).toLocalDate().equals(requestedDate))
                    .toList();
        }
        return new MarketCandleResponse(
                pair,
                range.name().toLowerCase(Locale.ROOT),
                request.interval(),
                zoneId.getId(),
                requestedFrom,
                points.isEmpty() ? null : points.getFirst().time(),
                points.isEmpty() ? null : points.getLast().time(),
                false,
                points
        );
    }

    List<MarketCandlePoint> mapCandles(List<List<Object>> rawCandles) {
        if (rawCandles == null || rawCandles.isEmpty()) {
            return List.of();
        }
        List<MarketCandlePoint> points = new ArrayList<>();
        for (List<Object> candle : rawCandles) {
            if (candle == null || candle.size() < 6) {
                continue;
            }
            points.add(new MarketCandlePoint(
                    Instant.ofEpochMilli(longValue(candle.get(0))),
                    doubleValue(candle.get(1)),
                    doubleValue(candle.get(2)),
                    doubleValue(candle.get(3)),
                    doubleValue(candle.get(4)),
                    doubleValue(candle.get(5))
            ));
        }
        return points;
    }

    private String normalizePair(String symbol) {
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        return normalized.endsWith(QUOTE_ASSET) ? normalized : normalized + QUOTE_ASSET;
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(value.toString());
    }

    private record CandleRequest(String interval, int limit) {
        private static CandleRequest forRange(MarketChartRange range) {
            return switch (range) {
                case TODAY, YESTERDAY -> new CandleRequest("5m", 300);
                case WEEK -> new CandleRequest("15m", 700);
                case MONTH -> new CandleRequest("4h", 200);
                case YEAR -> new CandleRequest("1d", 370);
            };
        }
    }
}
