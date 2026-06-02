package com.prognimak.marketbot.repository;

import com.prognimak.marketbot.entity.StockCatalogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockCatalogRepository extends JpaRepository<StockCatalogEntity, Long> {
    List<StockCatalogEntity> findByEnabledTrueOrderBySymbolAsc();

    Optional<StockCatalogEntity> findBySymbolIgnoreCase(String symbol);

    boolean existsBySymbolIgnoreCase(String symbol);
}
