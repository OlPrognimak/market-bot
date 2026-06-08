package com.prognimak.marketbot.news.service;

import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.news.entity.NewsArticleEntity;
import com.prognimak.marketbot.news.entity.NewsInsightEntity;
import com.prognimak.marketbot.news.model.InstrumentType;
import com.prognimak.marketbot.news.model.NewsArticle;
import com.prognimak.marketbot.news.model.NewsDirection;
import com.prognimak.marketbot.news.model.NewsImpactAnalysis;
import com.prognimak.marketbot.news.model.NewsRefreshResponse;
import com.prognimak.marketbot.news.provider.NewsProvider;
import com.prognimak.marketbot.news.repository.NewsArticleRepository;
import com.prognimak.marketbot.news.repository.NewsInsightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsMonitoringService {
    private final ConcurrentHashMap<String, ReentrantLock> refreshLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> activeRefreshes = new ConcurrentHashMap<>();
    private final AppProperties properties;
    private final List<NewsProvider> providers;
    private final NewsArticleRepository articleRepository;
    private final NewsInsightRepository insightRepository;
    private final NewsInstrumentAccessService accessService;
    private final NewsImpactAnalysisService analysisService;
    private final NewsInsightQueryService queryService;
    private final NewsRefreshExecutor refreshExecutor;

    public NewsRefreshResponse startRefresh(Long userId, InstrumentType type, String symbol) {
        requireNewsEnabled();
        NewsInstrumentAccessService.InstrumentDetails instrument = accessService.requireAccess(userId, type, symbol);
        String refreshKey = refreshKey(type, instrument.symbol());
        if (activeRefreshes.putIfAbsent(refreshKey, Boolean.TRUE) == null) {
            refreshExecutor.execute(() -> runBackgroundRefresh(userId, type, instrument.symbol(), refreshKey));
            log.info("News refresh accepted for user {}, instrument {}:{}.", userId, type, instrument.symbol());
        } else {
            log.info("News refresh already running for instrument {}:{}.", type, instrument.symbol());
        }
        return status(userId, type, instrument.symbol());
    }

    public NewsRefreshResponse status(Long userId, InstrumentType type, String symbol) {
        requireNewsEnabled();
        NewsInstrumentAccessService.InstrumentDetails instrument = accessService.requireAccess(userId, type, symbol);
        return new NewsRefreshResponse(
                0,
                0,
                activeRefreshes.containsKey(refreshKey(type, instrument.symbol())),
                queryService.find(userId, type, instrument.symbol(), properties.newsMonitoring().maxArticlesPerInstrument())
        );
    }

    private void runBackgroundRefresh(Long userId, InstrumentType type, String symbol, String refreshKey) {
        try {
            refresh(userId, type, symbol);
        } catch (RuntimeException exception) {
            log.error(
                    "Background news refresh failed for user {}, instrument {}:{}: {}",
                    userId,
                    type,
                    symbol,
                    exception.getMessage(),
                    exception
            );
        } finally {
            activeRefreshes.remove(refreshKey);
        }
    }

    private NewsRefreshResponse refresh(Long userId, InstrumentType type, String symbol) {
        String refreshKey = refreshKey(type, symbol);
        ReentrantLock refreshLock = refreshLocks.computeIfAbsent(refreshKey, ignored -> new ReentrantLock());
        refreshLock.lock();
        try {
            return refreshLocked(userId, type, symbol);
        } catch (RuntimeException exception) {
            log.error(
                    "News refresh failed for user {}, instrument {}:{}: {}",
                    userId,
                    type,
                    symbol,
                    exception.getMessage(),
                    exception
            );
            throw exception;
        } finally {
            refreshLock.unlock();
        }
    }

    private NewsRefreshResponse refreshLocked(Long userId, InstrumentType type, String symbol) {
        requireNewsEnabled();

        NewsInstrumentAccessService.InstrumentDetails instrument = accessService.requireAccess(userId, type, symbol);
        log.info(
                "News refresh started for user {}, instrument {}:{}; OpenAI analysis configured: {}.",
                userId,
                type,
                instrument.symbol(),
                analysisService.isAnalysisConfigured()
        );
        Instant now = Instant.now();
        Instant from = now.minus(properties.newsMonitoring().lookbackHours(), ChronoUnit.HOURS);
        List<NewsArticle> collected = providers.stream()
                .flatMap(provider -> provider.findNews(instrument.symbol(), instrument.name(), from, now).stream())
                .sorted(Comparator.comparing(NewsArticle::publishedAt).reversed())
                .limit(properties.newsMonitoring().maxArticlesPerInstrument())
                .toList();
        log.info("News providers returned {} articles for {}:{}.", collected.size(), type, instrument.symbol());

        int newInsights = 0;
        int existingInsights = 0;
        for (NewsArticle source : collected) {
            try {
                NewsArticleEntity article = findOrCreateArticle(source);
                NewsInsightEntity existingInsight = insightRepository
                        .findByArticleIdAndInstrumentTypeAndSymbolIgnoreCase(article.getId(), type, instrument.symbol())
                        .orElse(null);
                if (existingInsight != null) {
                    existingInsights++;
                    reanalyzeUnknownInsight(existingInsight, article, instrument, now);
                    continue;
                }
                NewsImpactAnalysis analysis = analysisService.analyze(article, instrument);
                try {
                    insightRepository.saveAndFlush(toInsight(article, instrument, analysis, now));
                    newInsights++;
                } catch (DataIntegrityViolationException duplicateInsight) {
                    if (!insightRepository.existsByArticleIdAndInstrumentTypeAndSymbolIgnoreCase(
                            article.getId(), type, instrument.symbol())) {
                        throw duplicateInsight;
                    }
                }
            } catch (RuntimeException exception) {
                log.error(
                        "News refresh skipped article {} ({}) for {}:{} after processing failure: {}",
                        source.providerArticleId(),
                        source.title(),
                        type,
                        instrument.symbol(),
                        exception.getMessage(),
                        exception
                );
            }
        }

        log.info(
                "News refresh completed for {}:{}: {} new insights, {} existing insights.",
                type,
                instrument.symbol(),
                newInsights,
                existingInsights
        );
        return new NewsRefreshResponse(
                collected.size(),
                newInsights,
                false,
                queryService.find(userId, type, instrument.symbol(), properties.newsMonitoring().maxArticlesPerInstrument())
        );
    }

    private void requireNewsEnabled() {
        if (!properties.newsMonitoring().enabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "News monitoring is disabled");
        }
    }

    private String refreshKey(InstrumentType type, String symbol) {
        return type.name() + ":" + symbol.trim().toUpperCase(Locale.ROOT);
    }

    private void reanalyzeUnknownInsight(
            NewsInsightEntity insight,
            NewsArticleEntity article,
            NewsInstrumentAccessService.InstrumentDetails instrument,
            Instant analyzedAt
    ) {
        if (insight.getDirection() != NewsDirection.UNKNOWN || !analysisService.isAnalysisConfigured()) {
            return;
        }
        NewsImpactAnalysis analysis = analysisService.analyze(article, instrument);
        insight.setDirection(analysis.direction());
        insight.setConfidence(analysis.confidence());
        insight.setImpactScore(analysis.impactScore());
        insight.setTimeHorizon(analysis.timeHorizon());
        insight.setSummary(limit(analysis.summary(), 2000));
        insight.setReason(limit(analysis.reason(), 2000));
        insight.setAlertRecommended(analysis.alertRecommended());
        insight.setModelName(analysisService.modelName());
        insight.setPromptVersion(analysisService.promptVersion());
        insight.setAnalyzedAt(analyzedAt);
        insight.setValidUntil(analyzedAt.plus(properties.newsMonitoring().maximumAnalysisAgeHours(), ChronoUnit.HOURS));
        insightRepository.saveAndFlush(insight);
    }

    private NewsArticleEntity findOrCreateArticle(NewsArticle source) {
        String contentHash = contentHash(source);
        return articleRepository.findByProviderAndProviderArticleId(source.provider(), source.providerArticleId())
                .or(() -> articleRepository.findByContentHash(contentHash))
                .orElseGet(() -> {
                    NewsArticleEntity entity = new NewsArticleEntity();
                    entity.setProvider(limit(source.provider(), 40));
                    entity.setProviderArticleId(limit(source.providerArticleId(), 160));
                    entity.setTitle(limit(source.title(), 500));
                    entity.setSummary(limit(source.summary(), 4000));
                    entity.setSourceName(limit(source.sourceName(), 160));
                    entity.setSourceUrl(limit(source.sourceUrl(), 1500));
                    entity.setPublishedAt(source.publishedAt());
                    entity.setContentHash(contentHash);
                    try {
                        return articleRepository.saveAndFlush(entity);
                    } catch (DataIntegrityViolationException duplicateArticle) {
                        return articleRepository.findByProviderAndProviderArticleId(
                                        source.provider(), source.providerArticleId())
                                .or(() -> articleRepository.findByContentHash(contentHash))
                                .orElseThrow(() -> duplicateArticle);
                    }
                });
    }

    private NewsInsightEntity toInsight(
            NewsArticleEntity article,
            NewsInstrumentAccessService.InstrumentDetails instrument,
            NewsImpactAnalysis analysis,
            Instant analyzedAt
    ) {
        NewsInsightEntity entity = new NewsInsightEntity();
        entity.setArticle(article);
        entity.setInstrumentType(instrument.type());
        entity.setSymbol(instrument.symbol());
        entity.setInstrumentName(limit(instrument.name(), 160));
        entity.setDirection(analysis.direction());
        entity.setConfidence(analysis.confidence());
        entity.setImpactScore(analysis.impactScore());
        entity.setTimeHorizon(analysis.timeHorizon());
        entity.setSummary(limit(analysis.summary(), 2000));
        entity.setReason(limit(analysis.reason(), 2000));
        entity.setAlertRecommended(analysis.alertRecommended());
        entity.setModelName(analysisService.modelName());
        entity.setPromptVersion(analysisService.promptVersion());
        entity.setAnalyzedAt(analyzedAt);
        entity.setValidUntil(analyzedAt.plus(properties.newsMonitoring().maximumAnalysisAgeHours(), ChronoUnit.HOURS));
        return entity;
    }

    private String contentHash(NewsArticle article) {
        String normalized = String.join("|",
                normalize(article.title()),
                normalize(article.sourceName()),
                article.publishedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
        );
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String limit(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }
}
