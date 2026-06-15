package com.prognimak.marketbot.dashboard.model;

/**
 * Exposes the effective trend profile used for a share in dashboard responses.
 */
public record TrendProfileInfo(
        String name,
        double longWeight,
        double shortWeight,
        double minimumScorePercent,
        double confidence
) {
}
