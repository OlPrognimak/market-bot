package com.prognimak.marketbot.model;

import java.util.List;

public record YahooChartResponse(Chart chart) {

    public record Chart(
            List<Result> result,
            YahooError error
    ) {
    }

    public record Result(
            Meta meta,
            List<Long> timestamp,
            Indicators indicators
    ) {
    }

    public record Meta(
            String symbol,
            Double regularMarketPrice,
            Double previousClose,
            Double chartPreviousClose,
            Long regularMarketTime,
            String exchangeName,
            String exchangeTimezoneName,
            Integer gmtoffset,
            TradingPeriods currentTradingPeriod,
            String currency,
            String exchange,
            String fullExchangeName,
            String instrumentType,
            String shortName,
            String longName
    ) {
        public Meta(String symbol, Double regularMarketPrice, Double previousClose, Double chartPreviousClose) {
            this(symbol, regularMarketPrice, previousClose, chartPreviousClose, null, null, null, null, null,
                    null, null, null, null, null, null);
        }

        public Meta(String symbol, Double regularMarketPrice, Double previousClose, Double chartPreviousClose,
                    Long regularMarketTime, String exchangeName, String exchangeTimezoneName, Integer gmtoffset,
                    TradingPeriods currentTradingPeriod) {
            this(symbol, regularMarketPrice, previousClose, chartPreviousClose, regularMarketTime, exchangeName,
                    exchangeTimezoneName, gmtoffset, currentTradingPeriod, null, null, null, null, null, null);
        }
    }

    public record Indicators(List<QuoteData> quote) {
    }

    public record QuoteData(
            List<Double> open,
            List<Double> high,
            List<Double> low,
            List<Double> close,
            List<Double> volume
    ) {
        public QuoteData(List<Double> open, List<Double> high, List<Double> low, List<Double> close) {
            this(open, high, low, close, null);
        }
    }

    public record TradingPeriods(TradingPeriod pre, TradingPeriod regular, TradingPeriod post) {
    }

    public record TradingPeriod(String timezone, long start, long end, int gmtoffset) {
    }

    public record YahooError(
            String code,
            String description
    ) {
    }
}
