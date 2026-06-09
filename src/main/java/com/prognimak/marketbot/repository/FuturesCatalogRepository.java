package com.prognimak.marketbot.repository;

import com.prognimak.marketbot.entity.FuturesCatalogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FuturesCatalogRepository extends JpaRepository<FuturesCatalogEntity, Long> {
    List<FuturesCatalogEntity> findByEnabledTrueOrderBySymbolAsc();
    Optional<FuturesCatalogEntity> findBySymbolIgnoreCase(String symbol);
}
