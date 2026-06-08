package com.prognimak.marketbot.news.model;

import java.util.List;

public record NewsRefreshResponse(
        int collectedArticles,
        int newInsights,
        boolean refreshing,
        List<NewsInsightResponse> insights
) {
}
