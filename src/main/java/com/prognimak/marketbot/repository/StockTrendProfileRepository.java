package com.prognimak.marketbot.repository;

import com.prognimak.marketbot.entity.StockTrendProfileEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockTrendProfileRepository extends JpaRepository<StockTrendProfileEntity, Long> {
    Optional<StockTrendProfileEntity> findByStockSymbolIgnoreCase(String symbol);

    @EntityGraph(attributePaths = "stock")
    List<StockTrendProfileEntity> findAllByStockEnabledTrue();
}
