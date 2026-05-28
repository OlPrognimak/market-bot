package com.prognimak.marketbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "crypto_quote",
        indexes = {
                @Index(name = "idx_crypto_quote_symbol_created", columnList = "symbol, created"),
                @Index(name = "idx_crypto_quote_base_asset_created", columnList = "baseAsset, created")
        }
)
@Getter
@Setter
public class CryptoQuoteEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String symbol;

    @Column(nullable = false, length = 30)
    private String baseAsset;

    @Column(nullable = false, length = 160)
    private String coinName;

    @Column(name = "scan_window", nullable = false, length = 10)
    private String window;

    private double openPrice;
    private double closePrice;
    private double highPrice;
    private double lowPrice;
    private double priceChangePercent;
    private double delta;
    private double quoteVolume;
    private boolean alert;
    private boolean send;
}
