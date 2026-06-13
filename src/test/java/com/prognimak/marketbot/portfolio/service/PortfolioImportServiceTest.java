package com.prognimak.marketbot.portfolio.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
