package com.prognimak.marketbot.portfolio.service;

import com.prognimak.marketbot.entity.AppUserEntity;
import com.prognimak.marketbot.portfolio.entity.*;
import com.prognimak.marketbot.portfolio.model.*;
import com.prognimak.marketbot.portfolio.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PortfolioImportService {
    private static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    private final PortfolioImportRepository importRepository;
    private final PortfolioTransactionRepository transactionRepository;
    private final PortfolioRealizedLotRepository realizedLotRepository;
    private final PortfolioIncomeRepository incomeRepository;
    private final ProviderSymbolMappingService symbolMappingService;

    @Transactional
    public PortfolioImportResponse importFile(
            AppUserEntity user,
            PortfolioProviderType providerType,
            MultipartFile file
    ) {
        byte[] bytes = readBytes(file);
        String fileHash = sha256(bytes);
        List<List<String>> rows = parseCsv(new String(bytes, StandardCharsets.UTF_8));
        PortfolioImportSchema schema = detectSchema(rows);
        PortfolioProviderType detectedProviderType = providerType(schema);
        Optional<PortfolioImportEntity> existingImport =
                importRepository.findByUserIdAndProviderTypeAndFileHash(user.getId(), detectedProviderType, fileHash);
        if (existingImport.isPresent()) {
            return toResponse(existingImport.get(), true);
        }

        PortfolioImportEntity portfolioImport = new PortfolioImportEntity();
        portfolioImport.setUser(user);
        portfolioImport.setProviderType(detectedProviderType);
        portfolioImport.setSchemaType(schema);
        portfolioImport.setOriginalFileName(Optional.ofNullable(file.getOriginalFilename()).orElse("portfolio.csv"));
        portfolioImport.setFileHash(fileHash);
        portfolioImport = importRepository.save(portfolioImport);

        ImportCounts counts = switch (schema) {
            case REVOLUT_ALL_TRANSACTIONS -> importTransactions(user, detectedProviderType, portfolioImport, rows);
            case REVOLUT_GAIN_LOSS_STATEMENT -> importStatement(user, detectedProviderType, portfolioImport, rows);
            case TRADE_REPUBLIC_TRANSACTION_EXPORT ->
                    importTradeRepublicTransactions(user, detectedProviderType, portfolioImport, rows);
            case TRADE_REPUBLIC_TAX_OVERVIEW ->
                    importTradeRepublicTaxOverview(user, detectedProviderType, portfolioImport, rows);
        };

        portfolioImport.setTotalRows(counts.total());
        portfolioImport.setImportedRows(counts.imported());
        portfolioImport.setSkippedRows(counts.skipped());
        return toResponse(importRepository.save(portfolioImport), false);
    }

    @Transactional(readOnly = true)
    public List<PortfolioImportResponse> imports(Long userId) {
        return importRepository.findByUserIdOrderByCreatedDesc(userId).stream()
                .map(item -> toResponse(item, false))
                .toList();
    }

    private ImportCounts importTransactions(
            AppUserEntity user,
            PortfolioProviderType providerType,
            PortfolioImportEntity portfolioImport,
            List<List<String>> rows
    ) {
        requireHeader(rows.getFirst(), List.of(
                "Date", "Ticker", "Type", "Quantity", "Price per share", "Total Amount", "Currency", "FX Rate"));
        int imported = 0;
        int skipped = 0;
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }
            requireColumns(row, 8, index + 1);
            String fingerprint = sha256(String.join("\u001f", row).getBytes(StandardCharsets.UTF_8));
            if (transactionRepository.existsByUserIdAndProviderTypeAndRecordFingerprint(
                    user.getId(), providerType, fingerprint)) {
                skipped++;
                continue;
            }

            PortfolioTransactionEntity entity = new PortfolioTransactionEntity();
            entity.setUser(user);
            entity.setPortfolioImport(portfolioImport);
            entity.setProviderType(providerType);
            entity.setRecordFingerprint(fingerprint);
            entity.setSourceRowNumber(index + 1);
            entity.setEventTime(Instant.parse(row.get(0)));
            entity.setTicker(revolutSymbolOrNull(row.get(1)));
            entity.setTransactionType(row.get(2).trim());
            entity.setQuantity(decimalOrNull(row.get(3)));
            entity.setPricePerShare(moneyOrNull(row.get(4)));
            entity.setTotalAmount(money(row.get(5)));
            entity.setCurrency(row.get(6).trim().toUpperCase(Locale.ROOT));
            entity.setFxRate(decimal(row.get(7)));
            transactionRepository.save(entity);
            imported++;
        }
        return new ImportCounts(imported + skipped, imported, skipped);
    }

    private ImportCounts importTradeRepublicTransactions(
            AppUserEntity user,
            PortfolioProviderType providerType,
            PortfolioImportEntity portfolioImport,
            List<List<String>> rows
    ) {
        requireHeaderContains(rows.getFirst(), List.of(
                "datetime", "category", "type", "asset_class", "name", "symbol", "shares", "price",
                "amount", "fee", "tax", "currency", "transaction_id"));
        Map<String, Integer> header = headerIndex(rows.getFirst());
        int imported = 0;
        int skipped = 0;
        int total = 0;
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            if (row.stream().allMatch(String::isBlank)
                    || !"TRADING".equals(value(row, header, "category"))) {
                continue;
            }
            total++;
            String type = value(row, header, "type");
            if (!"BUY".equals(type) && !"SELL".equals(type)) {
                continue;
            }
            String fingerprint = value(row, header, "transaction_id");
            if (fingerprint.isBlank()) {
                fingerprint = sha256(String.join("\u001f", row).getBytes(StandardCharsets.UTF_8));
            }
            if (transactionRepository.existsByUserIdAndProviderTypeAndRecordFingerprint(
                    user.getId(), providerType, fingerprint)) {
                skipped++;
                continue;
            }

            PortfolioTransactionEntity entity = new PortfolioTransactionEntity();
            entity.setUser(user);
            entity.setPortfolioImport(portfolioImport);
            entity.setProviderType(providerType);
            entity.setRecordFingerprint(fingerprint);
            entity.setSourceRowNumber(index + 1);
            entity.setEventTime(Instant.parse(value(row, header, "datetime")));
            entity.setTicker(symbolMappingService.resolveMarketSymbol(providerType, value(row, header, "symbol")));
            entity.setTransactionType(type);
            entity.setQuantity(decimal(value(row, header, "shares")).abs());
            entity.setPricePerShare(decimalOrNull(value(row, header, "price")));
            entity.setTotalAmount(tradeRepublicNetAmount(row, header).abs());
            entity.setCurrency(value(row, header, "currency").toUpperCase(Locale.ROOT));
            entity.setFxRate(BigDecimal.ONE);
            transactionRepository.save(entity);
            imported++;
        }
        return new ImportCounts(total, imported, skipped);
    }

    private ImportCounts importTradeRepublicTaxOverview(
            AppUserEntity user,
            PortfolioProviderType providerType,
            PortfolioImportEntity portfolioImport,
            List<List<String>> rows
    ) {
        requireHeaderContains(rows.getFirst(), List.of(
                "Datum", "Transaktionen", "ISIN", "Name", "Total", "Gewinn/Verlust", "Summe"));
        Map<String, Integer> header = headerIndex(rows.getFirst());
        int imported = 0;
        int skipped = 0;
        int total = 0;
        Map<String, Integer> occurrences = new HashMap<>();
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }
            total++;
            if (!"SELL".equals(value(row, header, "Transaktionen"))) {
                continue;
            }
            String fingerprint = sha256(String.join("\u001f", row).getBytes(StandardCharsets.UTF_8));
            int occurrence = occurrences.merge(fingerprint, 1, Integer::sum);
            if (realizedLotRepository.existsByUserIdAndProviderTypeAndRecordFingerprintAndOccurrenceOrdinal(
                    user.getId(), providerType, fingerprint, occurrence)) {
                skipped++;
                continue;
            }

            LocalDate soldDate = LocalDateTime.parse(value(row, header, "Datum")).toLocalDate();
            BigDecimal proceeds = decimal(value(row, header, "Total"));
            BigDecimal pnl = decimal(value(row, header, "Gewinn/Verlust"));
            PortfolioRealizedLotEntity entity = new PortfolioRealizedLotEntity();
            entity.setUser(user);
            entity.setPortfolioImport(portfolioImport);
            entity.setProviderType(providerType);
            entity.setRecordFingerprint(fingerprint);
            entity.setOccurrenceOrdinal(occurrence);
            entity.setSourceRowNumber(index + 1);
            entity.setAcquiredDate(soldDate);
            entity.setSoldDate(soldDate);
            entity.setSymbol(symbolMappingService.resolveMarketSymbol(providerType, value(row, header, "ISIN")));
            entity.setSecurityName(normalizeName(value(row, header, "Name")));
            entity.setIsin(value(row, header, "ISIN"));
            entity.setCountry("");
            entity.setQuantity(BigDecimal.ZERO);
            entity.setCostBasis(proceeds.subtract(pnl));
            entity.setGrossProceeds(proceeds);
            entity.setGrossPnl(pnl);
            entity.setCurrency("EUR");
            realizedLotRepository.save(entity);
            imported++;
        }
        return new ImportCounts(total, imported, skipped);
    }

    private ImportCounts importStatement(
            AppUserEntity user,
            PortfolioProviderType providerType,
            PortfolioImportEntity portfolioImport,
            List<List<String>> rows
    ) {
        int section = 0;
        int imported = 0;
        int skipped = 0;
        int total = 0;
        Map<String, Integer> occurrences = new HashMap<>();

        for (int index = 0; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            if (row.isEmpty() || row.stream().allMatch(String::isBlank)) {
                continue;
            }
            if (row.size() == 1 && "Income from Sells".equals(row.getFirst().trim())) {
                section = 1;
                continue;
            }
            if (row.size() == 1 && "Other income & fees".equals(row.getFirst().trim())) {
                section = 2;
                continue;
            }
            if ("Date acquired".equals(row.getFirst()) || "Date".equals(row.getFirst())) {
                continue;
            }

            if (section == 0) {
                throw badRequest("Unsupported gain/loss statement structure");
            }
            total++;
            String fingerprint = sha256(String.join("\u001f", row).getBytes(StandardCharsets.UTF_8));
            int occurrence = occurrences.merge(section + ":" + fingerprint, 1, Integer::sum);
            boolean exists = section == 1
                    ? realizedLotRepository.existsByUserIdAndProviderTypeAndRecordFingerprintAndOccurrenceOrdinal(
                            user.getId(), providerType, fingerprint, occurrence)
                    : incomeRepository.existsByUserIdAndProviderTypeAndRecordFingerprintAndOccurrenceOrdinal(
                            user.getId(), providerType, fingerprint, occurrence);
            if (exists) {
                skipped++;
                continue;
            }

            if (section == 1) {
                saveRealizedLot(user, providerType, portfolioImport, row, index + 1, fingerprint, occurrence);
            } else {
                saveIncome(user, providerType, portfolioImport, row, index + 1, fingerprint, occurrence);
            }
            imported++;
        }
        return new ImportCounts(total, imported, skipped);
    }

    private void saveRealizedLot(
            AppUserEntity user,
            PortfolioProviderType providerType,
            PortfolioImportEntity portfolioImport,
            List<String> row,
            int rowNumber,
            String fingerprint,
            int occurrence
    ) {
        requireColumns(row, 11, rowNumber);
        PortfolioRealizedLotEntity entity = new PortfolioRealizedLotEntity();
        entity.setUser(user);
        entity.setPortfolioImport(portfolioImport);
        entity.setProviderType(providerType);
        entity.setRecordFingerprint(fingerprint);
        entity.setOccurrenceOrdinal(occurrence);
        entity.setSourceRowNumber(rowNumber);
        entity.setAcquiredDate(LocalDate.parse(row.get(0)));
        entity.setSoldDate(LocalDate.parse(row.get(1)));
        entity.setSymbol(symbolMappingService.resolveMarketSymbol(providerType, row.get(2)));
        entity.setSecurityName(normalizeName(row.get(3)));
        entity.setIsin(row.get(4).trim());
        entity.setCountry(row.get(5).trim());
        entity.setQuantity(decimal(row.get(6)));
        entity.setCostBasis(decimal(row.get(7)));
        entity.setGrossProceeds(decimal(row.get(8)));
        entity.setGrossPnl(decimal(row.get(9)));
        entity.setCurrency(row.get(10).trim().toUpperCase(Locale.ROOT));
        realizedLotRepository.save(entity);
    }

    private void saveIncome(
            AppUserEntity user,
            PortfolioProviderType providerType,
            PortfolioImportEntity portfolioImport,
            List<String> row,
            int rowNumber,
            String fingerprint,
            int occurrence
    ) {
        requireColumns(row, 9, rowNumber);
        PortfolioIncomeEntity entity = new PortfolioIncomeEntity();
        entity.setUser(user);
        entity.setPortfolioImport(portfolioImport);
        entity.setProviderType(providerType);
        entity.setRecordFingerprint(fingerprint);
        entity.setOccurrenceOrdinal(occurrence);
        entity.setSourceRowNumber(rowNumber);
        entity.setIncomeDate(LocalDate.parse(row.get(0)));
        entity.setSymbol(symbolMappingService.resolveMarketSymbol(providerType, row.get(1)));
        entity.setSecurityName(normalizeName(row.get(2)));
        entity.setIsin(row.get(3).trim());
        entity.setCountry(row.get(4).trim());
        entity.setGrossAmount(money(row.get(5)));
        entity.setWithholdingTax(money(row.get(6)));
        entity.setNetAmount(money(row.get(7)));
        entity.setCurrency(row.get(8).trim().toUpperCase(Locale.ROOT));
        incomeRepository.save(entity);
    }

    private PortfolioImportSchema detectSchema(List<List<String>> rows) {
        if (rows.isEmpty() || rows.getFirst().isEmpty()) {
            throw badRequest("CSV file is empty");
        }
        String first = rows.getFirst().getFirst().replace("\uFEFF", "").trim();
        if ("Date".equals(first) && rows.getFirst().contains("Ticker") && rows.getFirst().contains("FX Rate")) {
            return PortfolioImportSchema.REVOLUT_ALL_TRANSACTIONS;
        }
        if ("Income from Sells".equals(first)) {
            return PortfolioImportSchema.REVOLUT_GAIN_LOSS_STATEMENT;
        }
        if ("datetime".equals(first) && rows.getFirst().contains("transaction_id")) {
            return PortfolioImportSchema.TRADE_REPUBLIC_TRANSACTION_EXPORT;
        }
        if ("Datum".equals(first) && rows.getFirst().contains("Gewinn/Verlust")) {
            return PortfolioImportSchema.TRADE_REPUBLIC_TAX_OVERVIEW;
        }
        throw badRequest("Unsupported portfolio CSV schema");
    }

    private PortfolioProviderType providerType(PortfolioImportSchema schema) {
        return switch (schema) {
            case REVOLUT_ALL_TRANSACTIONS, REVOLUT_GAIN_LOSS_STATEMENT -> PortfolioProviderType.REVOLUT;
            case TRADE_REPUBLIC_TRANSACTION_EXPORT, TRADE_REPUBLIC_TAX_OVERVIEW -> PortfolioProviderType.TRADE_REPUBLIC;
        };
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("CSV file is required");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw badRequest("CSV file exceeds 5 MB");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw badRequest("Could not read CSV file");
        }
    }

    static List<List<String>> parseCsv(String content) {
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < content.length() && content.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                row.add(field.toString());
                field.setLength(0);
            } else if ((current == '\n' || current == '\r') && !quoted) {
                if (current == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(current);
            }
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static BigDecimal money(String value) {
        String normalized = value.trim().replace("€", "").replace("$", "");
        int separator = normalized.indexOf(' ');
        return decimal(separator >= 0 ? normalized.substring(separator + 1) : normalized);
    }

    private static BigDecimal moneyOrNull(String value) {
        return value == null || value.isBlank() ? null : money(value);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value.trim());
    }

    private static BigDecimal decimalOrNull(String value) {
        return value == null || value.isBlank() ? null : decimal(value);
    }

    private static BigDecimal decimalOrZero(String value) {
        return value == null || value.isBlank() ? BigDecimal.ZERO : decimal(value);
    }

    private static BigDecimal tradeRepublicNetAmount(List<String> row, Map<String, Integer> header) {
        return decimal(value(row, header, "amount"))
                .add(decimalOrZero(value(row, header, "fee")))
                .add(decimalOrZero(value(row, header, "tax")));
    }

    private static String normalizeName(String value) {
        return value.trim().replace("&amp;", "&");
    }

    private static Map<String, Integer> headerIndex(List<String> header) {
        Map<String, Integer> indexes = new HashMap<>();
        for (int index = 0; index < header.size(); index++) {
            indexes.put(header.get(index), index);
        }
        return indexes;
    }

    private static String value(List<String> row, Map<String, Integer> header, String name) {
        Integer index = header.get(name);
        return index == null || index >= row.size() ? "" : row.get(index).trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String revolutSymbolOrNull(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : symbolMappingService.resolveMarketSymbol(PortfolioProviderType.REVOLUT, normalized);
    }

    private static void requireHeader(List<String> actual, List<String> expected) {
        if (!actual.equals(expected)) {
            throw badRequest("Unsupported all-transactions CSV header");
        }
    }

    private static void requireHeaderContains(List<String> actual, List<String> required) {
        if (!actual.containsAll(required)) {
            throw badRequest("Unsupported portfolio CSV header");
        }
    }

    private static void requireColumns(List<String> row, int expected, int rowNumber) {
        if (row.size() != expected) {
            throw badRequest("Invalid column count at CSV row " + rowNumber);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static PortfolioImportResponse toResponse(PortfolioImportEntity item, boolean duplicateFile) {
        return new PortfolioImportResponse(
                item.getId(),
                item.getProviderType(),
                item.getSchemaType(),
                item.getOriginalFileName(),
                item.getFileHash(),
                item.getTotalRows(),
                duplicateFile ? 0 : item.getImportedRows(),
                duplicateFile ? item.getTotalRows() : item.getSkippedRows(),
                item.getCreated(),
                duplicateFile
        );
    }

    private record ImportCounts(int total, int imported, int skipped) {
    }
}
