package com.prognimak.marketbot.portfolio.entity;

import com.prognimak.marketbot.entity.AbstractEntity;
import com.prognimak.marketbot.entity.AppUserEntity;
import com.prognimak.marketbot.portfolio.model.PortfolioProviderType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "portfolio_realized_lot", uniqueConstraints = @UniqueConstraint(
        name = "uk_portfolio_lot_user_provider_fingerprint_occurrence",
        columnNames = {"user_id", "provider_type", "record_fingerprint", "occurrence_ordinal"}
))
@Getter
@Setter
public class PortfolioRealizedLotEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_id", nullable = false)
    private PortfolioImportEntity portfolioImport;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 30)
    private PortfolioProviderType providerType;

    @Column(name = "record_fingerprint", nullable = false, length = 64)
    private String recordFingerprint;

    @Column(name = "occurrence_ordinal", nullable = false)
    private int occurrenceOrdinal;

    @Column(nullable = false)
    private int sourceRowNumber;

    @Column(nullable = false)
    private LocalDate acquiredDate;

    @Column(nullable = false)
    private LocalDate soldDate;

    @Column(nullable = false, length = 40)
    private String symbol;

    @Column(nullable = false, length = 200)
    private String securityName;

    @Column(nullable = false, length = 20)
    private String isin;

    @Column(length = 10)
    private String country;

    @Column(nullable = false, precision = 30, scale = 12)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 30, scale = 12)
    private BigDecimal costBasis;

    @Column(nullable = false, precision = 30, scale = 12)
    private BigDecimal grossProceeds;

    @Column(nullable = false, precision = 30, scale = 12)
    private BigDecimal grossPnl;

    @Column(nullable = false, length = 10)
    private String currency;
}
