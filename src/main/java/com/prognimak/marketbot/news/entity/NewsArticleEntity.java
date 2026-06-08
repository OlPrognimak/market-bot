package com.prognimak.marketbot.news.entity;

import com.prognimak.marketbot.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "news_article",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_news_article_provider_id", columnNames = {"provider", "provider_article_id"}),
                @UniqueConstraint(name = "uk_news_article_content_hash", columnNames = "content_hash")
        }
)
@Getter
@Setter
public class NewsArticleEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(name = "provider_article_id", nullable = false, length = 160)
    private String providerArticleId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 4000)
    private String summary;

    @Column(name = "source_name", length = 160)
    private String sourceName;

    @Column(name = "source_url", nullable = false, length = 1500)
    private String sourceUrl;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
}
