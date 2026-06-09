package com.prognimak.marketbot.entity;

import com.prognimak.marketbot.model.FreshnessStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "futures_quote", indexes = @Index(
        name = "idx_futures_quote_symbol_time", columnList = "symbol,provider_timestamp"
))
@Getter
@Setter
public class FuturesQuoteEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 30)
    private String symbol;
    @Column(name = "provider_symbol", nullable = false, length = 40)
    private String providerSymbol;
    private double price;
    private double priorSettlement;
    private double sessionOpen;
    private double changeFromSettlementPercent;
    private double changeFromOpenPercent;
    private double deltaPercent;
    private double rollingPercent;
    private double volume;
    @Column(nullable = false, length = 30)
    private String provider;
    @Column(name = "provider_timestamp", nullable = false)
    private Instant providerTimestamp;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FreshnessStatus freshnessStatus;
}
