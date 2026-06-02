package com.prognimak.marketbot.entity;

import com.prognimak.marketbot.model.WatchlistPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "stock_catalog",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_catalog_symbol", columnNames = "symbol")
)
@Getter
@Setter
public class StockCatalogEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String symbol;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 80)
    private String region;

    @Column(length = 120)
    private String sector;

    @Column(length = 80)
    private String exchange;

    @Column(length = 20)
    private String currency;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WatchlistPriority priority = WatchlistPriority.NORMAL;
}
