package com.prognimak.marketbot.client;

import java.util.List;

public record YahooResponse(QuoteResponse quoteResponse) {

    public record QuoteResponse(List<YahooQuote> result) {
    }

    public record YahooQuote(
            String symbol,
            Double regularMarketPrice,
            Double regularMarketChange,
            Double regularMarketChangePercent,
            Double regularMarketOpen,
            Double regularMarketDayHigh,
            Double regularMarketDayLow,
            Double regularMarketPreviousClose
    ) {
    }
}