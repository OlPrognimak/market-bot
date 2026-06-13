package com.prognimak.marketbot.portfolio.repository;

import com.prognimak.marketbot.portfolio.entity.PortfolioTransactionEntity;
import com.prognimak.marketbot.portfolio.model.PortfolioProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransactionEntity, Long> {
    boolean existsByUserIdAndProviderTypeAndRecordFingerprint(
            Long userId, PortfolioProviderType providerType, String recordFingerprint);

    List<PortfolioTransactionEntity> findByUserIdOrderByEventTimeAsc(Long userId);

    List<PortfolioTransactionEntity> findTop100ByUserIdOrderByEventTimeDesc(Long userId);

    List<PortfolioTransactionEntity> findByUserIdAndTickerIgnoreCaseOrderByEventTimeAsc(Long userId, String ticker);

    List<PortfolioTransactionEntity> findByUserIdAndTickerInOrderByEventTimeAsc(Long userId, List<String> tickers);
}
