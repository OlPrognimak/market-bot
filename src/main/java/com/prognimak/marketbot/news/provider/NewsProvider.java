package com.prognimak.marketbot.news.provider;

import com.prognimak.marketbot.news.model.NewsArticle;

import java.time.Instant;
import java.util.List;

public interface NewsProvider {
    List<NewsArticle> findNews(String symbol, String instrumentName, Instant from, Instant to);
}
