package com.prognimak.marketbot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "futures_catalog", uniqueConstraints = @UniqueConstraint(
        name = "uk_futures_catalog_symbol", columnNames = "symbol"
))
@Getter
@Setter
public class FuturesCatalogEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 30)
    private String symbol;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 120)
    private String underlying;
    @Column(length = 30)
    private String region;
    @Column(length = 80)
    private String exchange;
    @Column(length = 20)
    private String currency;
    @Column(name = "provider_symbol", nullable = false, length = 40)
    private String providerSymbol;
    @Column(name = "exchange_timezone", length = 80)
    private String exchangeTimezone;
    @Column(nullable = false)
    private boolean enabled = true;
}
