package com.prognimak.marketbot.portfolio.repository;

import com.prognimak.marketbot.portfolio.entity.PortfolioRealizedLotEntity;
import com.prognimak.marketbot.portfolio.model.PortfolioProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioRealizedLotRepository extends JpaRepository<PortfolioRealizedLotEntity, Long> {
    boolean existsByUserIdAndProviderTypeAndRecordFingerprintAndOccurrenceOrdinal(
            Long userId, PortfolioProviderType providerType, String recordFingerprint, int occurrenceOrdinal);

    List<PortfolioRealizedLotEntity> findByUserId(Long userId);
}
