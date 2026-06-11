package com.prognimak.marketbot.dashboard.service;

import com.prognimak.marketbot.client.YahooCandleClient;
import com.prognimak.marketbot.dashboard.model.MarketCandlePoint;
import com.prognimak.marketbot.dashboard.model.MarketCandleResponse;
import com.prognimak.marketbot.dashboard.model.MarketChartRange;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShareCandleServiceTest {
    private final YahooCandleClient client = mock(YahooCandleClient.class);
    private final ShareCandleService service = new ShareCandleService(client);

    @Test
    void filtersTodayUsingProviderExchangeTimeZone() {
        ZoneId zoneId = ZoneId.of("Europe/Berlin");
        LocalDate today = LocalDate.now(zoneId);
        MarketCandlePoint yesterday = point(today.minusDays(1).atTime(17, 0).atZone(zoneId).toInstant());
        MarketCandlePoint todayPoint = point(today.atTime(9, 0).atZone(zoneId).toInstant());
        when(client.candles("BMW.DE", MarketChartRange.TODAY)).thenReturn(new MarketCandleResponse(
                "BMW.DE", "today", "5m", zoneId.getId(), today.atStartOfDay(zoneId).toInstant(),
                yesterday.time(), todayPoint.time(), false, List.of(yesterday, todayPoint)
        ));

        MarketCandleResponse response = service.candles("BMW.DE", MarketChartRange.TODAY);

        assertEquals(1, response.points().size());
        assertEquals(todayPoint.time(), response.points().getFirst().time());
    }

    private MarketCandlePoint point(Instant time) {
        return new MarketCandlePoint(time, 100, 105, 99, 103, 1_000);
    }
}
