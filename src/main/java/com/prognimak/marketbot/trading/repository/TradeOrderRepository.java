package com.prognimak.marketbot.trading.repository;

import com.prognimak.marketbot.trading.entity.TradeOrderEntity;
import com.prognimak.marketbot.trading.model.TradeOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TradeOrderRepository extends JpaRepository<TradeOrderEntity, Long> {
    List<TradeOrderEntity> findByUserIdOrderByIdDesc(Long userId);

    Optional<TradeOrderEntity> findByUserIdAndPreviewId(Long userId, String previewId);

    long countByUserIdAndSymbolIgnoreCaseAndStatusIn(Long userId, String symbol, Collection<TradeOrderStatus> statuses);

    List<TradeOrderEntity> findByUserIdAndStatusAndExpiresAtBefore(Long userId, TradeOrderStatus status, Instant expiresAt);
}
