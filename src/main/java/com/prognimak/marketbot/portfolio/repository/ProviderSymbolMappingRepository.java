package com.prognimak.marketbot.portfolio.repository;

import com.prognimak.marketbot.portfolio.entity.ProviderSymbolMappingEntity;
import com.prognimak.marketbot.portfolio.model.PortfolioProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderSymbolMappingRepository extends JpaRepository<ProviderSymbolMappingEntity, Long> {
    List<ProviderSymbolMappingEntity> findByEnabledTrueOrderByProviderTypeAscSourceSymbolAscPriorityAsc();

    List<ProviderSymbolMappingEntity> findAllByOrderByProviderTypeAscSourceSymbolAscPriorityAsc();

    Optional<ProviderSymbolMappingEntity> findByProviderTypeAndSourceSymbolIgnoreCaseAndMarketProviderIgnoreCase(
            PortfolioProviderType providerType, String sourceSymbol, String marketProvider);

    boolean existsByProviderTypeAndSourceSymbolIgnoreCaseAndMarketProviderIgnoreCase(
            PortfolioProviderType providerType, String sourceSymbol, String marketProvider);
}
