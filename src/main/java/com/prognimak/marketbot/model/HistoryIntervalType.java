package com.prognimak.marketbot.model;

import java.time.LocalDate;

public enum HistoryIntervalType {
    DAILY("1d", 10),
    WEEKLY("1wk", 21),
    MONTHLY("1mo", 45);

    private final String yahooInterval;
    private final int lookbackDaysForPreviousClose;

    HistoryIntervalType(String yahooInterval, int lookbackDaysForPreviousClose) {
        this.yahooInterval = yahooInterval;
        this.lookbackDaysForPreviousClose = lookbackDaysForPreviousClose;
    }

    public String yahooInterval() {
        return yahooInterval;
    }

    public LocalDate requestFrom(LocalDate from) {
        return from.minusDays(lookbackDaysForPreviousClose);
    }
}
