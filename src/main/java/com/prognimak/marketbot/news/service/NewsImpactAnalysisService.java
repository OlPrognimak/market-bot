package com.prognimak.marketbot.news.service;

import com.prognimak.marketbot.news.entity.NewsArticleEntity;
import com.prognimak.marketbot.news.model.NewsDirection;
import com.prognimak.marketbot.news.model.NewsImpactAnalysis;
import com.prognimak.marketbot.news.model.NewsTimeHorizon;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class NewsImpactAnalysisService {
    private static final String PROMPT_VERSION = "v1";

    private final ChatClient chatClient;
    private final String modelName;
    private final boolean analysisConfigured;

    public NewsImpactAnalysisService(
            OpenAiChatModel openAiChatModel,
            @Value("${spring.ai.openai.chat.model:gpt-5-mini}") String modelName,
            @Value("${spring.ai.openai.api-key:}") String apiKey
    ) {
        this.chatClient = ChatClient.builder(openAiChatModel).build();
        this.modelName = modelName;
        this.analysisConfigured = apiKey != null
                && !apiKey.isBlank()
                && !"no-key-configured".equals(apiKey);
        if (!analysisConfigured) {
            log.warn("OpenAI news analysis is disabled: OPENAI_API_KEY is not configured.");
        }
    }

    public NewsImpactAnalysis analyze(
            NewsArticleEntity article,
            NewsInstrumentAccessService.InstrumentDetails instrument
    ) {
        if (!analysisConfigured) {
            log.warn(
                    "OpenAI news analysis skipped for symbol {}, article {}: OPENAI_API_KEY is not configured.",
                    instrument.symbol(),
                    article.getId()
            );
            return unavailable(article, "AI analysis disabled: OPENAI_API_KEY is not configured.");
        }
        try {
            NewsImpactAnalysis result = chatClient.prompt()
                    .system("""
                            Analyze financial news conservatively. Return UNKNOWN when evidence is insufficient.
                            Confidence and impactScore must be integers from 0 to 100.
                            Keep summary and reason concise, each below 1,500 characters.
                            Never invent facts. The sourceUrls list must contain the supplied source URL.
                            """)
                    .user("""
                            Instrument type: %s
                            Symbol: %s
                            Name: %s
                            Region: %s
                            Sector: %s
                            Exchange: %s
                            Headline: %s
                            Article summary: %s
                            Source URL: %s
                            """.formatted(
                            instrument.type(), instrument.symbol(), instrument.name(),
                            instrument.region(), instrument.sector(), instrument.exchange(),
                            article.getTitle(), article.getSummary(), article.getSourceUrl()
                    ))
                    .call()
                    .entity(NewsImpactAnalysis.class);
            NewsImpactAnalysis analysis = normalize(result, article);
            log.info(
                    "OpenAI news analysis completed for symbol {}, article {}: direction {}, confidence {}, impact {}.",
                    instrument.symbol(),
                    article.getId(),
                    analysis.direction(),
                    analysis.confidence(),
                    analysis.impactScore()
            );
            return analysis;
        } catch (RuntimeException exception) {
            log.error(
                    "OpenAI news analysis failed for symbol {}, article {} ({}): {}",
                    instrument.symbol(),
                    article.getId(),
                    article.getTitle(),
                    exception.getMessage()
            );
            log.debug("OpenAI news analysis failure for symbol {}, article {}", instrument.symbol(), article.getId(), exception);
            return unavailable(article, failureReason(exception));
        }
    }

    public String modelName() {
        return modelName;
    }

    public String promptVersion() {
        return PROMPT_VERSION;
    }

    public boolean isAnalysisConfigured() {
        return analysisConfigured;
    }

    private NewsImpactAnalysis normalize(NewsImpactAnalysis analysis, NewsArticleEntity article) {
        if (analysis == null) {
            return unavailable(article, "AI analysis failed: OpenAI returned no structured analysis.");
        }
        return new NewsImpactAnalysis(
                analysis.direction() == null ? NewsDirection.UNKNOWN : analysis.direction(),
                bounded(analysis.confidence()),
                bounded(analysis.impactScore()),
                analysis.timeHorizon() == null ? NewsTimeHorizon.UNKNOWN : analysis.timeHorizon(),
                textOrFallback(analysis.summary(), article.getSummary(), article.getTitle()),
                textOrFallback(analysis.reason(), "No reliable market-impact reason was returned."),
                analysis.alertRecommended(),
                List.of(article.getSourceUrl())
        );
    }

    private NewsImpactAnalysis unavailable(NewsArticleEntity article, String reason) {
        return new NewsImpactAnalysis(
                NewsDirection.UNKNOWN,
                0,
                0,
                NewsTimeHorizon.UNKNOWN,
                textOrFallback(article.getSummary(), article.getTitle()),
                reason,
                false,
                List.of(article.getSourceUrl())
        );
    }

    private String failureReason(RuntimeException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("429") || message.toLowerCase().contains("quota")) {
            return "AI analysis unavailable: OpenAI API quota exceeded.";
        }
        if (message.contains("401") || message.toLowerCase().contains("api key")) {
            return "AI analysis unavailable: OpenAI API key was rejected.";
        }
        return "AI analysis temporarily unavailable: OpenAI request failed.";
    }

    private int bounded(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String textOrFallback(String value, String... fallbacks) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        for (String fallback : fallbacks) {
            if (fallback != null && !fallback.isBlank()) {
                return fallback;
            }
        }
        return "No summary available.";
    }
}
