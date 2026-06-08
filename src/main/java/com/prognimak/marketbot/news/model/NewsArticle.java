package com.prognimak.marketbot.news.model;

import java.time.Instant;

public record NewsArticle(
        String provider,
        String providerArticleId,
        String title,
        String summary,
        String sourceName,
        String sourceUrl,
        Instant publishedAt
) {
}
