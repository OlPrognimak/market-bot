package com.prognimak.marketbot.repository;

import com.prognimak.marketbot.entity.StockTrendProfileEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Provides persistence access to current per-share trend profiles.
 */
public interface StockTrendProfileRepository extends JpaRepository<StockTrendProfileEntity, Long> {
    /** Finds the single current profile associated with a stock symbol. */
    Optional<StockTrendProfileEntity> findByStockSymbolIgnoreCase(String symbol);

    /** Loads profiles for enabled stocks together with their catalog entries for cache refresh. */
    @EntityGraph(attributePaths = "stock")
    List<StockTrendProfileEntity> findAllByStockEnabledTrue();
}
