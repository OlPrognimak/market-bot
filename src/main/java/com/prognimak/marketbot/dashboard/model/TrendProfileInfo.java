package com.prognimak.marketbot.dashboard.model;

public record TrendProfileInfo(
        String name,
        double longWeight,
        double shortWeight,
        double minimumScorePercent,
        double confidence
) {
}
