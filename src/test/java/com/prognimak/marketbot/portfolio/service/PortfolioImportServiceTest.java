package com.prognimak.marketbot.portfolio.service;

import com.prognimak.marketbot.entity.AppUserEntity;
import com.prognimak.marketbot.portfolio.entity.PortfolioImportEntity;
import com.prognimak.marketbot.portfolio.entity.PortfolioTransactionEntity;
import com.prognimak.marketbot.portfolio.model.PortfolioImportSchema;
import com.prognimak.marketbot.portfolio.model.PortfolioProviderType;
import com.prognimak.marketbot.portfolio.repository.PortfolioImportRepository;
import com.prognimak.marketbot.portfolio.repository.PortfolioIncomeRepository;
import com.prognimak.marketbot.portfolio.repository.PortfolioRealizedLotRepository;
import com.prognimak.marketbot.portfolio.repository.PortfolioTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioImportServiceTest {
    @Test
    void parsesQuotedFieldsAndRepeatedRowsWithoutLosingData() {
        String csv = "A,B,C\n"
                + "1,\"Name, Inc.\",\"quoted \"\"value\"\"\"\n"
                + "1,\"Name, Inc.\",\"quoted \"\"value\"\"\"\n";

        List<List<String>> rows = PortfolioImportService.parseCsv(csv);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(1)).containsExactly("1", "Name, Inc.", "quoted \"value\"");
        assertThat(rows.get(2)).isEqualTo(rows.get(1));
    }

    @Test
    void parsesRepresentativeRevolutSchemas() {
        String transactions = "Date,Ticker,Type,Quantity,Price per share,Total Amount,Currency,FX Rate\n"
                + "2025-09-22T17:08:53Z,ORCL,BUY - MARKET,1.53673094,USD 325.37,USD 500,USD,1.1815\n";
        String statement = "Income from Sells\n"
                + "Date acquired,Date sold,Symbol,Security name,ISIN,Country,Quantity,Cost basis,Gross proceeds,Gross PnL,Currency\n"
                + "2025-09-22,2025-09-26,ORCL,Oracle,US68389X1054,US,1,100.00,110.00,10.00,USD\n"
                + "\nOther income & fees\n"
                + "Date,Symbol,Security name,ISIN,Country,Gross amount,Withholding tax,Net Amount,Currency\n"
                + "2025-10-24,ORCL,Oracle dividend,US68389X1054,US,2.04,€0.30,€1.74,EUR\n";

        List<List<String>> transactionRows = PortfolioImportService.parseCsv(transactions);
        List<List<String>> statementRows = PortfolioImportService.parseCsv(statement);

        assertThat(transactionRows).hasSize(2);
        assertThat(transactionRows.getFirst()).containsExactly(
                "Date", "Ticker", "Type", "Quantity", "Price per share", "Total Amount", "Currency", "FX Rate");
        assertThat(statementRows).hasSize(7);
        assertThat(statementRows.getFirst()).containsExactly("Income from Sells");
        assertThat(statementRows).anySatisfy(row -> assertThat(row).containsExactly("Other income & fees"));
    }

    @Test
    void parsesRepresentativeTradeRepublicSchemas() {
        String transactions = "\"datetime\",\"date\",\"account_type\",\"category\",\"type\",\"asset_class\",\"name\",\"symbol\",\"shares\",\"price\",\"amount\",\"fee\",\"tax\",\"currency\",\"original_amount\",\"original_currency\",\"fx_rate\",\"description\",\"transaction_id\",\"counterparty_name\",\"counterparty_iban\",\"payment_reference\",\"mcc_code\"\n"
                + "\"2026-06-04T09:01:27.042Z\",\"2026-06-04\",\"DEFAULT\",\"TRADING\",\"SELL\",\"STOCK\",\"Micron Technology\",\"US5951121038\",\"-1.0000000000\",\"883.5000000000\",\"883.50\",\"-1.00\",\"\",\"EUR\",\"\",\"\",\"\",\"Sell trade\",\"tx-1\",\"\",\"\",\"\",\"\"\n";
        String tax = "Datum,Transaktionen,ISIN,Name,Total,Gewinn/Verlust,Teilfreistellung,Vorabpauschale,Verlusttopf Aktien,Verlusttopf Allgemein,Freistellungsauftrag,Bemessungsgrundl.,Quellensteuer,Kapitalertragsteuer,Quellensteuertopf,Solidaritätszuschlag,Kirchensteuer,Finanztransaktionssteuer,Gesamte Steuern,Summe\n"
                + "\"2026-06-04T11:01:29\",\"SELL\",\"US5951121038\",\"Micron Technology\",\"883.50\",\"-51.10\",\"\",\"\",\"51.10\",\"\",\"\",\"-51.10\",\"0\",\"0.00\",\"\",\"0.00\",\"\",\"\",\"0.00\",\"882.50\"\n";

        List<List<String>> transactionRows = PortfolioImportService.parseCsv(transactions);
        List<List<String>> taxRows = PortfolioImportService.parseCsv(tax);

        assertThat(transactionRows.getFirst()).contains("datetime", "transaction_id");
        assertThat(transactionRows.get(1)).contains("TRADING", "SELL", "US5951121038");
        assertThat(taxRows.getFirst()).contains("Datum", "Gewinn/Verlust");
        assertThat(taxRows.get(1)).contains("SELL", "-51.10");
    }

    @Test
    void importsTradeRepublicTransactionExportWithProviderSpecificExtraColumns() {
        PortfolioImportRepository importRepository = mock(PortfolioImportRepository.class);
        PortfolioTransactionRepository transactionRepository = mock(PortfolioTransactionRepository.class);
        PortfolioRealizedLotRepository realizedLotRepository = mock(PortfolioRealizedLotRepository.class);
        PortfolioIncomeRepository incomeRepository = mock(PortfolioIncomeRepository.class);
        PortfolioImportService service = new PortfolioImportService(
                importRepository, transactionRepository, realizedLotRepository, incomeRepository);
        AppUserEntity user = new AppUserEntity();
        user.setId(1L);
        String csv = "\"datetime\",\"date\",\"account_type\",\"category\",\"type\",\"asset_class\",\"name\",\"symbol\",\"shares\",\"price\",\"amount\",\"fee\",\"tax\",\"currency\",\"original_amount\",\"original_currency\",\"fx_rate\",\"description\",\"transaction_id\",\"counterparty_name\",\"counterparty_iban\",\"payment_reference\",\"mcc_code\"\n"
                + "\"2026-06-04T09:01:27.042Z\",\"2026-06-04\",\"DEFAULT\",\"TRADING\",\"SELL\",\"STOCK\",\"Micron Technology\",\"US5951121038\",\"-1.0000000000\",\"883.5000000000\",\"883.50\",\"-1.00\",\"\",\"EUR\",\"\",\"\",\"\",\"Sell trade\",\"tx-1\",\"\",\"\",\"\",\"\"\n";

        when(importRepository.findByUserIdAndProviderTypeAndFileHash(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(importRepository.save(any(PortfolioImportEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.existsByUserIdAndProviderTypeAndRecordFingerprint(any(), any(), any()))
                .thenReturn(false);
        when(transactionRepository.save(any(PortfolioTransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.importFile(user, PortfolioProviderType.TRADE_REPUBLIC,
                new MockMultipartFile("file", "Transaktionsexport.csv", "text/csv", csv.getBytes()));

        assertThat(response.providerType()).isEqualTo(PortfolioProviderType.TRADE_REPUBLIC);
        assertThat(response.schemaType()).isEqualTo(PortfolioImportSchema.TRADE_REPUBLIC_TRANSACTION_EXPORT);
        assertThat(response.totalRows()).isEqualTo(1);
        assertThat(response.importedRows()).isEqualTo(1);
        assertThat(response.skippedRows()).isZero();
    }
}
