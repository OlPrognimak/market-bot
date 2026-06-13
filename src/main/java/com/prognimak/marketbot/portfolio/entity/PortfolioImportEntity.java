package com.prognimak.marketbot.portfolio.entity;

import com.prognimak.marketbot.entity.AbstractEntity;
import com.prognimak.marketbot.entity.AppUserEntity;
import com.prognimak.marketbot.portfolio.model.PortfolioImportSchema;
import com.prognimak.marketbot.portfolio.model.PortfolioProviderType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "portfolio_import", uniqueConstraints = @UniqueConstraint(
        name = "uk_portfolio_import_user_provider_hash",
        columnNames = {"user_id", "provider_type", "file_hash"}
))
@Getter
@Setter
public class PortfolioImportEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 30)
    private PortfolioProviderType providerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PortfolioImportSchema schemaType;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(nullable = false)
    private int totalRows;

    @Column(nullable = false)
    private int importedRows;

    @Column(nullable = false)
    private int skippedRows;
}
