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
            Indicators indicators
    ) {
    }

    public record Meta(
            String symbol,
            Double regularMarketPrice,
            Double previousClose,
            Double chartPreviousClose
    ) {
    }

    public record Indicators(List<QuoteData> quote) {
    }

    public record QuoteData(
            List<Double> open,
            List<Double> high,
            List<Double> low,
            List<Double> close
    ) {
    }

    public record YahooError(
            String code,
            String description
    ) {
    }
}
