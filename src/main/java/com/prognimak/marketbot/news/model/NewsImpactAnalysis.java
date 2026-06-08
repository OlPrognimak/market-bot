package com.prognimak.marketbot.news.model;

import java.util.List;

public record NewsImpactAnalysis(
        NewsDirection direction,
        int confidence,
        int impactScore,
        NewsTimeHorizon timeHorizon,
        String summary,
        String reason,
        boolean alertRecommended,
        List<String> sourceUrls
) {
}
