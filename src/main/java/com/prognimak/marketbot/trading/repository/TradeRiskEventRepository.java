package com.prognimak.marketbot.trading.repository;

import com.prognimak.marketbot.trading.entity.TradeRiskEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRiskEventRepository extends JpaRepository<TradeRiskEventEntity, Long> {
}
