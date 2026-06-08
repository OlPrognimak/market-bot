package com.prognimak.marketbot.news.model;

import java.time.Instant;
import java.util.List;

public record NewsResearchResponse(
        InstrumentType instrumentType,
        String symbol,
        NewsResearchLayer layer,
        boolean running,
        String content,
        List<NewsResearchSource> sources,
        String error,
        Instant generatedAt
) {
}
