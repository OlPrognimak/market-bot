package com.prognimak.marketbot.repository;

import com.prognimak.marketbot.entity.CryptoCoinCatalogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CryptoCoinCatalogRepository extends JpaRepository<CryptoCoinCatalogEntity, Long> {
    List<CryptoCoinCatalogEntity> findByEnabledTrueOrderBySymbolAsc();

    Optional<CryptoCoinCatalogEntity> findBySymbolIgnoreCase(String symbol);

    boolean existsBySymbolIgnoreCase(String symbol);
}
