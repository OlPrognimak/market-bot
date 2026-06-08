package com.prognimak.marketbot.news.entity;

import com.prognimak.marketbot.entity.AbstractEntity;
import com.prognimak.marketbot.news.model.InstrumentType;
import com.prognimak.marketbot.news.model.NewsDirection;
import com.prognimak.marketbot.news.model.NewsTimeHorizon;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "news_insight",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_news_insight_article_instrument",
                columnNames = {"article_id", "instrument_type", "symbol"}
        )
)
@Getter
@Setter
public class NewsInsightEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private NewsArticleEntity article;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", nullable = false, length = 20)
    private InstrumentType instrumentType;

    @Column(nullable = false, length = 40)
    private String symbol;

    @Column(name = "instrument_name", nullable = false, length = 160)
    private String instrumentName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NewsDirection direction;

    @Column(nullable = false)
    private int confidence;

    @Column(name = "impact_score", nullable = false)
    private int impactScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_horizon", nullable = false, length = 20)
    private NewsTimeHorizon timeHorizon;

    @Column(nullable = false, length = 2000)
    private String summary;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(name = "alert_recommended", nullable = false)
    private boolean alertRecommended;

    @Column(name = "model_name", nullable = false, length = 80)
    private String modelName;

    @Column(name = "prompt_version", nullable = false, length = 30)
    private String promptVersion;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;
}
