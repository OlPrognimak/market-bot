package com.prognimak.marketbot.news.model;

import java.time.Instant;

public record NewsInsightResponse(
        Long id,
        InstrumentType instrumentType,
        String symbol,
        String instrumentName,
        NewsDirection direction,
        int confidence,
        int impactScore,
        NewsTimeHorizon timeHorizon,
        String summary,
        String reason,
        boolean alertRecommended,
        String title,
        String articleSummary,
        String sourceName,
        String sourceUrl,
        Instant publishedAt,
        Instant analyzedAt
) {
}
