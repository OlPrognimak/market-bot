package com.prognimak.marketbot.news.repository;

import com.prognimak.marketbot.news.entity.NewsInsightEntity;
import com.prognimak.marketbot.news.model.InstrumentType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NewsInsightRepository extends JpaRepository<NewsInsightEntity, Long> {
    boolean existsByArticleIdAndInstrumentTypeAndSymbolIgnoreCase(Long articleId, InstrumentType instrumentType, String symbol);

    Optional<NewsInsightEntity> findByArticleIdAndInstrumentTypeAndSymbolIgnoreCase(
            Long articleId,
            InstrumentType instrumentType,
            String symbol
    );

    @EntityGraph(attributePaths = "article")
    List<NewsInsightEntity> findByInstrumentTypeAndSymbolIgnoreCaseOrderByAnalyzedAtDesc(
            InstrumentType instrumentType,
            String symbol,
            Pageable pageable
    );
}
