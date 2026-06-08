package com.prognimak.marketbot.news.provider;

import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.news.model.NewsArticle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@Order(100)
@Slf4j
public class FinnhubNewsProvider implements NewsProvider {
    private final WebClient webClient;
    private final AppProperties properties;

    public FinnhubNewsProvider(WebClient.Builder builder, AppProperties properties) {
        this.webClient = builder.baseUrl("https://finnhub.io/api/v1").build();
        this.properties = properties;
    }

    @Override
    public List<NewsArticle> findNews(String symbol, String instrumentName, Instant from, Instant to) {
        if (!properties.newsMonitoring().providers().finnhubEnabled()) {
            return List.of();
        }
        if (!isApiKeyConfigured()) {
            log.warn("Finnhub news provider is enabled but FINNHUB_API_KEY is not configured.");
            return List.of();
        }
        try {
            FinnhubArticle[] response = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/company-news")
                            .queryParam("symbol", symbol)
                            .queryParam("from", from.atZone(ZoneOffset.UTC).toLocalDate())
                            .queryParam("to", to.atZone(ZoneOffset.UTC).toLocalDate())
                            .queryParam("token", properties.providers().finnhubApiKey())
                            .build())
                    .retrieve()
                    .bodyToMono(FinnhubArticle[].class)
                    .block();
            if (response == null) {
                return List.of();
            }
            List<NewsArticle> articles = Arrays.stream(response)
                    .filter(article -> article.url() != null && !article.url().isBlank())
                    .filter(article -> article.headline() != null && !article.headline().isBlank())
                    .filter(article -> article.datetime() > 0)
                    .map(article -> new NewsArticle(
                            "FINNHUB",
                            article.id() == null
                                    ? UUID.nameUUIDFromBytes(article.url().getBytes(StandardCharsets.UTF_8)).toString()
                                    : article.id().toString(),
                            article.headline(),
                            article.summary(),
                            article.source(),
                            article.url(),
                            Instant.ofEpochSecond(article.datetime())
                    ))
                    .filter(article -> !article.publishedAt().isBefore(from) && !article.publishedAt().isAfter(to))
                    .toList();
            log.info("Finnhub news returned {} matching articles for {}.", articles.size(), symbol);
            return articles;
        } catch (RuntimeException exception) {
            log.warn("Finnhub news request failed for {}: {}", symbol, exception.getMessage());
            log.debug("Finnhub news request failure for {}", symbol, exception);
            return List.of();
        }
    }

    private boolean isApiKeyConfigured() {
        String apiKey = properties.providers().finnhubApiKey();
        return apiKey != null
                && !apiKey.isBlank()
                && !"no-key-configured".equals(apiKey)
                && !"xxxx".equals(apiKey);
    }

    private record FinnhubArticle(
            Long id,
            long datetime,
            String headline,
            String source,
            String summary,
            String url
    ) {
    }
}
