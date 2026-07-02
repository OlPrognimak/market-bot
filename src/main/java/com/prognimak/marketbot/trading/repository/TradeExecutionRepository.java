package com.prognimak.marketbot.trading.repository;

import com.prognimak.marketbot.trading.entity.TradeExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeExecutionRepository extends JpaRepository<TradeExecutionEntity, Long> {
    List<TradeExecutionEntity> findByUserIdOrderByExecutedAtDesc(Long userId);

    List<TradeExecutionEntity> findByUserIdAndSymbolIgnoreCaseOrderByExecutedAtDesc(Long userId, String symbol);
}
