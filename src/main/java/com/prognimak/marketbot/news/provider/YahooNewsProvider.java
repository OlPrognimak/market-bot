package com.prognimak.marketbot.news.provider;

import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.news.model.NewsArticle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Component
@Order(0)
@Slf4j
public class YahooNewsProvider implements NewsProvider {
    private final WebClient webClient;
    private final AppProperties properties;

    public YahooNewsProvider(WebClient.Builder builder, AppProperties properties) {
        this.webClient = builder.baseUrl("https://query2.finance.yahoo.com").build();
        this.properties = properties;
    }

    @Override
    public List<NewsArticle> findNews(String symbol, String instrumentName, Instant from, Instant to) {
        if (!properties.newsMonitoring().providers().yahooEnabled()) {
            return List.of();
        }

        try {
            YahooSearchResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/finance/search")
                            .queryParam("q", symbol)
                            .queryParam("quotesCount", 1)
                            .queryParam("newsCount", properties.newsMonitoring().maxArticlesPerInstrument())
                            .build())
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                    .retrieve()
                    .bodyToMono(YahooSearchResponse.class)
                    .block();

            if (response == null || response.news() == null) {
                return List.of();
            }

            String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
            List<NewsArticle> articles = response.news().stream()
                    .filter(article -> article.uuid() != null && !article.uuid().isBlank())
                    .filter(article -> article.title() != null && !article.title().isBlank())
                    .filter(article -> article.link() != null && !article.link().isBlank())
                    .filter(article -> article.providerPublishTime() > 0)
                    .filter(article -> article.relatedTickers() == null
                            || article.relatedTickers().isEmpty()
                            || article.relatedTickers().stream()
                            .map(ticker -> ticker.toUpperCase(Locale.ROOT))
                            .anyMatch(normalizedSymbol::equals))
                    .map(article -> new NewsArticle(
                            "YAHOO",
                            article.uuid(),
                            article.title(),
                            null,
                            article.publisher(),
                            article.link(),
                            Instant.ofEpochSecond(article.providerPublishTime())
                    ))
                    .filter(article -> !article.publishedAt().isBefore(from) && !article.publishedAt().isAfter(to))
                    .toList();
            log.info("Yahoo news returned {} matching articles for {}.", articles.size(), symbol);
            return articles;
        } catch (RuntimeException exception) {
            log.warn("Yahoo news request failed for {}: {}", symbol, exception.getMessage());
            log.debug("Yahoo news request failure for {}", symbol, exception);
            return List.of();
        }
    }

    private record YahooSearchResponse(List<YahooNewsItem> news) {
    }

    private record YahooNewsItem(
            String uuid,
            String title,
            String publisher,
            String link,
            long providerPublishTime,
            List<String> relatedTickers
    ) {
    }
}
