package com.prognimak.marketbot.news.service;

import com.prognimak.marketbot.news.entity.NewsInsightEntity;
import com.prognimak.marketbot.news.model.InstrumentType;
import com.prognimak.marketbot.news.model.NewsInsightResponse;
import com.prognimak.marketbot.news.repository.NewsInsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsInsightQueryService {
    private final NewsInsightRepository repository;
    private final NewsInstrumentAccessService accessService;

    @Transactional(readOnly = true)
    public List<NewsInsightResponse> find(Long userId, InstrumentType type, String symbol, int requestedLimit) {
        NewsInstrumentAccessService.InstrumentDetails instrument = accessService.requireAccess(userId, type, symbol);
        int limit = Math.max(1, Math.min(100, requestedLimit));
        return repository.findByInstrumentTypeAndSymbolIgnoreCaseOrderByAnalyzedAtDesc(
                        type,
                        instrument.symbol(),
                        PageRequest.of(0, limit)
                ).stream()
                .map(this::toResponse)
                .toList();
    }

    public NewsInsightResponse toResponse(NewsInsightEntity entity) {
        return new NewsInsightResponse(
                entity.getId(),
                entity.getInstrumentType(),
                entity.getSymbol(),
                entity.getInstrumentName(),
                entity.getDirection(),
                entity.getConfidence(),
                entity.getImpactScore(),
                entity.getTimeHorizon(),
                entity.getSummary(),
                entity.getReason(),
                entity.isAlertRecommended(),
                entity.getArticle().getTitle(),
                entity.getArticle().getSummary(),
                entity.getArticle().getSourceName(),
                entity.getArticle().getSourceUrl(),
                entity.getArticle().getPublishedAt(),
                entity.getAnalyzedAt()
        );
    }
}
