package com.prognimak.marketbot.news.service;

import com.prognimak.marketbot.news.model.InstrumentType;
import com.prognimak.marketbot.news.model.NewsResearchLayer;
import com.prognimak.marketbot.news.model.NewsResearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class NewsResearchService {
    private final ConcurrentHashMap<String, NewsResearchResponse> results = new ConcurrentHashMap<>();
    private final NewsInstrumentAccessService accessService;
    private final NewsImpactAnalysisService analysisService;
    private final NewsRefreshExecutor executor;
    private final OpenAiWebResearchClient webResearchClient;

    public NewsResearchService(
            NewsInstrumentAccessService accessService,
            NewsImpactAnalysisService analysisService,
            NewsRefreshExecutor executor,
            OpenAiWebResearchClient webResearchClient
    ) {
        this.accessService = accessService;
        this.analysisService = analysisService;
        this.executor = executor;
        this.webResearchClient = webResearchClient;
    }

    public NewsResearchResponse start(Long userId, InstrumentType type, String symbol, NewsResearchLayer layer) {
        NewsInstrumentAccessService.InstrumentDetails instrument = accessService.requireAccess(userId, type, symbol);
        if (!analysisService.isAnalysisConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OPENAI_API_KEY is not configured");
        }
        String key = key(userId, type, instrument.symbol(), layer);
        NewsResearchResponse current = results.get(key);
        if (current != null && current.running()) {
            return current;
        }
        NewsResearchResponse running = new NewsResearchResponse(type, instrument.symbol(), layer, true, null, List.of(), null, null);
        results.put(key, running);
        executor.execute(() -> analyze(userId, instrument, layer, key));
        log.info("AI research accepted for user {}, instrument {}:{}, layer {}.", userId, type, instrument.symbol(), layer);
        return running;
    }

    public NewsResearchResponse status(Long userId, InstrumentType type, String symbol, NewsResearchLayer layer) {
        NewsInstrumentAccessService.InstrumentDetails instrument = accessService.requireAccess(userId, type, symbol);
        return results.getOrDefault(
                key(userId, type, instrument.symbol(), layer),
                new NewsResearchResponse(type, instrument.symbol(), layer, false, null, List.of(), null, null)
        );
    }

    private void analyze(
            Long userId,
            NewsInstrumentAccessService.InstrumentDetails instrument,
            NewsResearchLayer layer,
        String key
    ) {
        try {
            OpenAiWebResearchClient.WebResearchResult research = webResearchClient.research(buildPrompt(instrument, layer));
            results.put(key, new NewsResearchResponse(
                    instrument.type(), instrument.symbol(), layer, false, research.content(), research.sources(), null, Instant.now()
            ));
            log.info(
                    "OpenAI web research completed for user {}, instrument {}:{}, layer {}, model {}, sources {}.",
                    userId,
                    instrument.type(),
                    instrument.symbol(),
                    layer,
                    webResearchClient.model(),
                    research.sources().size()
            );
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null ? "AI research request failed." : exception.getMessage();
            results.put(key, new NewsResearchResponse(
                    instrument.type(), instrument.symbol(), layer, false, null, List.of(), message, Instant.now()
            ));
            log.error(
                    "OpenAI web research failed for user {}, instrument {}:{}, layer {}: {}",
                    userId,
                    instrument.type(),
                    instrument.symbol(),
                    layer,
                    message,
                    exception
            );
        }
    }

    private String buildPrompt(
            NewsInstrumentAccessService.InstrumentDetails instrument,
            NewsResearchLayer layer
    ) {
        return """
                Conduct current, web-based investment research for the following instrument.
                Search the live web before answering. Prioritize primary and authoritative sources:
                company website, investor relations, regulatory filings, earnings releases,
                exchange announcements, reputable financial reporting, and recent relevant news.
                Cross-check important claims across multiple sources.

                You are a cautious investment research analyst.
                Never invent financial data, valuation metrics, events, dates, or source claims.
                Clearly distinguish facts from inference and uncertainty.
                Produce readable Markdown with short sections, bullets, and bold labels.
                Cite sources inline with links and end with a Sources section containing the most important links.
                State the date or reporting period for time-sensitive figures.
                Do not answer only whether price will go up or down.
                End with a concise Evidence limitations section.

                Instrument: %s (%s)
                Type: %s
                Region: %s
                Sector: %s
                Exchange: %s
                Requested layer: %s

                Required questions and structure:
                %s
                """.formatted(
                instrument.name(),
                instrument.symbol(),
                instrument.type(),
                value(instrument.region()),
                value(instrument.sector()),
                value(instrument.exchange()),
                layer,
                questions(layer)
        );
    }

    private String questions(NewsResearchLayer layer) {
        return switch (layer) {
            case CURRENT_SITUATION -> """
                    # Current Situation
                    1. Why is the stock moving right now?
                    2. What are the three most important positive catalysts?
                    3. What are the three most important negative catalysts?
                    4. Is market sentiment too optimistic, balanced, or too pessimistic?
                    5. Would you buy, hold, or avoid it today, and why?
                    6. What would need to happen for that opinion to change?
                    """;
            case FUNDAMENTAL_ANALYSIS -> """
                    # Fundamental Analysis
                    1. What does the evidence indicate about business quality and competitive position?
                    2. What fundamental strengths and weaknesses are visible?
                    3. What important fundamental information is missing?
                    4. Which claims require confirmation from filings, earnings, or investor relations sources?
                    5. Give a cautious buy, hold, or avoid view based only on available evidence.
                    """;
            case SCENARIO_ANALYSIS -> """
                    # Scenario Analysis
                    1. Describe a bull, base, and bear scenario for the next 90 days.
                    2. What events could move the stock by more than 10%?
                    3. What is the biggest risk investors may underestimate?
                    4. What is the biggest opportunity investors may underestimate?
                    5. List the signals that would confirm or invalidate each scenario.
                    """;
            case COMPLETE_RESEARCH -> """
                    # Current Situation
                    Answer why the stock is moving, the top three positive and negative catalysts, and current sentiment.
                    # Fundamental Analysis
                    Assess visible business strengths and weaknesses, and clearly identify missing financial evidence.
                    # Scenario Analysis
                    Give bull, base, and bear 90-day scenarios, events that could move the stock by more than 10%,
                    underestimated risk and opportunity, a buy/hold/avoid view, and what would change that view.
                    """;
        };
    }

    private String key(Long userId, InstrumentType type, String symbol, NewsResearchLayer layer) {
        return userId + ":" + type + ":" + symbol.trim().toUpperCase(Locale.ROOT) + ":" + layer;
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }
}
