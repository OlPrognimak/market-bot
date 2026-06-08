package com.prognimak.marketbot.news.repository;

import com.prognimak.marketbot.news.entity.NewsArticleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NewsArticleRepository extends JpaRepository<NewsArticleEntity, Long> {
    Optional<NewsArticleEntity> findByProviderAndProviderArticleId(String provider, String providerArticleId);

    Optional<NewsArticleEntity> findByContentHash(String contentHash);
}
