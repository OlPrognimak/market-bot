package com.prognimak.marketbot.portfolio.repository;

import com.prognimak.marketbot.portfolio.entity.PortfolioImportEntity;
import com.prognimak.marketbot.portfolio.model.PortfolioProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioImportRepository extends JpaRepository<PortfolioImportEntity, Long> {
    Optional<PortfolioImportEntity> findByUserIdAndProviderTypeAndFileHash(
            Long userId, PortfolioProviderType providerType, String fileHash);

    List<PortfolioImportEntity> findByUserIdOrderByCreatedDesc(Long userId);
}
