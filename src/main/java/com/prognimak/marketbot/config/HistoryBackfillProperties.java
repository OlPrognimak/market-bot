package com.prognimak.marketbot.config;

import com.prognimak.marketbot.model.HistoryIntervalType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;

@ConfigurationProperties(prefix = "market-bot.history")
public record HistoryBackfillProperties(
        LocalDate from,
        LocalDate to,
        String symbols,
        String source,
        HistoryIntervalType intervalType,
        long requestDelayMs
) {
    public LocalDate effectiveFrom() {
        return from == null ? LocalDate.now().withDayOfYear(1) : from;
    }

    public LocalDate effectiveTo() {
        return to == null ? LocalDate.now() : to;
    }

    public String effectiveSource() {
        return source == null || source.isBlank() ? "YAHOO" : source;
    }

    public HistoryIntervalType effectiveIntervalType() {
        return intervalType == null ? HistoryIntervalType.DAILY : intervalType;
    }

    public long effectiveRequestDelayMs() {
        return Math.max(requestDelayMs, 0);
    }
}
