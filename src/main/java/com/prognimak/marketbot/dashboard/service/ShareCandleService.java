package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.client.YahooCandleClient;
import com.prognimak.marketbot.dashboard.model.MarketCandlePoint;
import com.prognimak.marketbot.dashboard.model.MarketCandleResponse;
import com.prognimak.marketbot.dashboard.model.MarketChartRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShareCandleService {
    private final YahooCandleClient yahooCandleClient;

    public MarketCandleResponse candles(String symbol, MarketChartRange range) {
        MarketCandleResponse response = yahooCandleClient.candles(symbol, range);
        if (range != MarketChartRange.TODAY && range != MarketChartRange.YESTERDAY) {
            return response;
        }

        ZoneId zoneId = ZoneId.of(response.timeZone());
        LocalDate requestedDate = range == MarketChartRange.TODAY
                ? LocalDate.now(zoneId)
                : LocalDate.now(zoneId).minusDays(1);
        List<MarketCandlePoint> requestedPoints = pointsForDate(response.points(), requestedDate, zoneId);
        boolean fallback = false;
        if (requestedPoints.isEmpty() && range == MarketChartRange.TODAY && !response.points().isEmpty()) {
            LocalDate latestDate = response.points().getLast().time().atZone(zoneId).toLocalDate();
            requestedPoints = pointsForDate(response.points(), latestDate, zoneId);
            fallback = latestDate.isBefore(requestedDate);
        }

        return new MarketCandleResponse(
                response.symbol(),
                response.range(),
                response.interval(),
                response.timeZone(),
                response.requestedFrom(),
                requestedPoints.isEmpty() ? null : requestedPoints.getFirst().time(),
                requestedPoints.isEmpty() ? null : requestedPoints.getLast().time(),
                fallback,
                requestedPoints
        );
    }

    private List<MarketCandlePoint> pointsForDate(List<MarketCandlePoint> points, LocalDate date, ZoneId zoneId) {
        return points.stream()
                .filter(point -> point.time().atZone(zoneId).toLocalDate().equals(date))
                .toList();
    }
}
