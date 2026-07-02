package com.prognimak.marketbot.trading.repository;

import com.prognimak.marketbot.trading.entity.PaperPositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaperPositionRepository extends JpaRepository<PaperPositionEntity, Long> {
    List<PaperPositionEntity> findByUserIdOrderBySymbolAsc(Long userId);

    Optional<PaperPositionEntity> findByUserIdAndSymbolIgnoreCase(Long userId, String symbol);
}
