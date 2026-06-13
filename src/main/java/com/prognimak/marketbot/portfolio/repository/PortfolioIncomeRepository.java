package com.prognimak.marketbot.portfolio.repository;

import com.prognimak.marketbot.portfolio.entity.PortfolioIncomeEntity;
import com.prognimak.marketbot.portfolio.model.PortfolioProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioIncomeRepository extends JpaRepository<PortfolioIncomeEntity, Long> {
    boolean existsByUserIdAndProviderTypeAndRecordFingerprintAndOccurrenceOrdinal(
            Long userId, PortfolioProviderType providerType, String recordFingerprint, int occurrenceOrdinal);

    List<PortfolioIncomeEntity> findByUserId(Long userId);
}
