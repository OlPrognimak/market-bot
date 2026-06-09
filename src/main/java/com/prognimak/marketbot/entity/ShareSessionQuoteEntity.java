package com.prognimak.marketbot.entity;

import com.prognimak.marketbot.model.FreshnessStatus;
import com.prognimak.marketbot.model.MarketSession;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "share_session_quote", indexes = @Index(
        name = "idx_share_session_quote_symbol_session_time",
        columnList = "symbol,session_type,provider_timestamp"
))
@Getter
@Setter
public class ShareSessionQuoteEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40)
    private String symbol;
    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 30)
    private MarketSession sessionType;
    @Column(length = 80)
    private String exchange;
    @Column(name = "exchange_timezone", length = 80)
    private String exchangeTimezone;
    private double price;
    private double baselinePrice;
    private double changePercent;
    private double deltaPercent;
    private double rollingPercent;
    private double open;
    private double high;
    private double low;
    private double volume;
    @Column(nullable = false, length = 30)
    private String provider;
    @Column(name = "provider_timestamp", nullable = false)
    private Instant providerTimestamp;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FreshnessStatus freshnessStatus;
}
