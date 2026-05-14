package com.prognimak.marketbot;

public record Quote(
        String symbol,
        double current,
        double change,
        double percentChange,
        double high,
        double low,
        double open,
        double previousClose
) {}
