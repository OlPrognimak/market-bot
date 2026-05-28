package com.prognimak.marketbot.dashboard.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public enum MarketChartRange {
    TODAY,
    YESTERDAY,
    WEEK,
    MONTH,
    YEAR;

    public Instant start(ZoneId zoneId) {
        LocalDate today = LocalDate.now(zoneId);
        return switch (this) {
            case TODAY -> today.atStartOfDay(zoneId).toInstant();
            case YESTERDAY -> today.minusDays(1).atStartOfDay(zoneId).toInstant();
            case WEEK -> today.minusDays(7).atStartOfDay(zoneId).toInstant();
            case MONTH -> today.minusDays(31).atStartOfDay(zoneId).toInstant();
            case YEAR -> today.minusDays(366).atStartOfDay(zoneId).toInstant();
        };
    }

    public static MarketChartRange parse(String value) {
        if (value == null || value.isBlank()) {
            return TODAY;
        }

        return MarketChartRange.valueOf(value.trim().toUpperCase());
    }
}
