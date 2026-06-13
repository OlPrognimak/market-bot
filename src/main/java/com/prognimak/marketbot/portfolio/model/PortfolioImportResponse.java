package com.prognimak.marketbot.portfolio.model;

import java.time.Instant;

public record PortfolioImportResponse(
        Long id,
        PortfolioProviderType providerType,
        PortfolioImportSchema schemaType,
        String originalFileName,
        String fileHash,
        int totalRows,
        int importedRows,
        int skippedRows,
        Instant importedAt,
        boolean duplicateFile
) {
}
