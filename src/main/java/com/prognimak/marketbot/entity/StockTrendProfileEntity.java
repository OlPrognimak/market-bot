package com.prognimak.marketbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Persists the current effective adaptive trend profile and calibration metrics for one stock.
 *
 * <p>There is at most one profile row per stock catalog entry. A {@code DEFAULT} row records that
 * fallback parameters are active while insufficient scanner history prevents adaptive calibration.</p>
 */
@Entity
@Table(name = "stock_trend_profile", uniqueConstraints = @UniqueConstraint(
        name = "uk_stock_trend_profile_stock", columnNames = "stock_id"
))
@Getter
@Setter
public class StockTrendProfileEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private StockCatalogEntity stock;

    @Column(name = "profile_name", nullable = false, length = 40)
    private String profileName;

    @Column(name = "long_weight", nullable = false)
    private double longWeight;

    @Column(name = "short_weight", nullable = false)
    private double shortWeight;

    @Column(name = "minimum_score_percent", nullable = false)
    private double minimumScorePercent;

    @Column(nullable = false)
    private double confidence;

    @Column(name = "quality_score", nullable = false)
    private double qualityScore;

    @Column(name = "sample_size", nullable = false)
    private int sampleSize;

    @Column(name = "trading_days", nullable = false)
    private int tradingDays;

    @Column(name = "movement_frequency", nullable = false)
    private double movementFrequency;

    @Column(name = "reversal_frequency", nullable = false)
    private double reversalFrequency;

    @Column(name = "continuation_rate", nullable = false)
    private double continuationRate;

    @Column(name = "typical_movement_percent", nullable = false)
    private double typicalMovementPercent;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;
}
