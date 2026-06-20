package com.prognimak.marketbot.portfolio.entity;

import com.prognimak.marketbot.entity.AbstractEntity;
import com.prognimak.marketbot.portfolio.model.PortfolioProviderType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Maps a provider-specific portfolio symbol or identifier to a market-data provider symbol.
 */
@Entity
@Table(name = "provider_symbol_mapping", uniqueConstraints = @UniqueConstraint(
        name = "uk_provider_symbol_mapping_source",
        columnNames = {"provider_type", "source_symbol", "market_provider"}
))
@Getter
@Setter
public class ProviderSymbolMappingEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 30)
    private PortfolioProviderType providerType;

    @Column(name = "source_symbol", nullable = false, length = 80)
    private String sourceSymbol;

    @Column(name = "source_symbol_type", nullable = false, length = 30)
    private String sourceSymbolType = "UNKNOWN";

    @Column(name = "market_provider", nullable = false, length = 30)
    private String marketProvider = "YAHOO";

    @Column(name = "market_symbol", nullable = false, length = 80)
    private String marketSymbol;

    @Column(name = "instrument_name", length = 200)
    private String instrumentName;

    @Column(length = 10)
    private String currency;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean verified;

    @Column(nullable = false)
    private int priority = 100;
}
