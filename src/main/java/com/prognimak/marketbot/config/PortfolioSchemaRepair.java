package com.prognimak.marketbot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!history-backfill")
@RequiredArgsConstructor
public class PortfolioSchemaRepair implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        repairProviderTypeConstraint("portfolio_import");
        repairProviderTypeConstraint("portfolio_transaction");
        repairProviderTypeConstraint("portfolio_realized_lot");
        repairProviderTypeConstraint("portfolio_income");
        repairImportSchemaConstraint();
        repairTradeRepublicSymbols();
        repairRevolutSymbols();
    }

    private void repairProviderTypeConstraint(String tableName) {
        jdbcTemplate.execute("alter table marketbot." + tableName
                + " drop constraint if exists " + tableName + "_provider_type_check");
        jdbcTemplate.execute("alter table marketbot." + tableName
                + " add constraint " + tableName + "_provider_type_check"
                + " check (provider_type in ('REVOLUT', 'TRADE_REPUBLIC'))");
        log.info("Verified {}.provider_type check constraint.", tableName);
    }

    private void repairImportSchemaConstraint() {
        jdbcTemplate.execute("""
                alter table marketbot.portfolio_import
                    drop constraint if exists portfolio_import_schema_type_check
                """);
        jdbcTemplate.execute("""
                alter table marketbot.portfolio_import
                    add constraint portfolio_import_schema_type_check
                        check (schema_type in (
                            'REVOLUT_ALL_TRANSACTIONS',
                            'REVOLUT_GAIN_LOSS_STATEMENT',
                            'TRADE_REPUBLIC_TRANSACTION_EXPORT',
                            'TRADE_REPUBLIC_TAX_OVERVIEW'
                        ))
                """);
        log.info("Verified portfolio_import.schema_type check constraint.");
    }

    private void repairTradeRepublicSymbols() {
        String[][] aliases = {
                {"IE0000ZL1RD2", "C8PX.DE"},
                {"US82621A2033", "ENR.DE"},
                {"SMNEY", "ENR.DE"},
                {"DE0006599905", "MRK.DE"},
                {"GB0002634946", "BSP.DE"},
                {"CA53680V1076", "LSPD.TO"},
                {"US0258161092", "AXP"},
                {"US0605051046", "BAC"},
                {"US46625H1005", "JPM"},
                {"US7069151055", "PENG"},
                {"US7509171069", "RMBS"},
                {"US84615Q1031", "SPCX"}
        };
        RepairCounts counts = repairAliases("TRADE_REPUBLIC", aliases);
        log.info("Repaired Trade Republic aliases: {} transactions, {} realized lots, {} income rows.",
                counts.transactionRows(), counts.realizedRows(), counts.incomeRows());
    }

    private void repairRevolutSymbols() {
        String[][] aliases = {
                {"ABJ", "ABBN.SW"},
                {"ASME", "ASML"},
                {"SGM", "STM"},
                {"IRBTQ", "IRBT"},
                {"AIR1", "AIR.PA"},
                {"ENR1", "ENR.DE"},
                {"SEJ1", "SAF.PA"},
                {"XFB", "XFAB.PA"}
        };
        RepairCounts counts = repairAliases("REVOLUT", aliases);
        log.info("Repaired Revolut aliases: {} transactions, {} realized lots, {} income rows.",
                counts.transactionRows(), counts.realizedRows(), counts.incomeRows());
    }

    private RepairCounts repairAliases(String providerType, String[][] aliases) {
        int transactionRows = 0;
        int realizedRows = 0;
        int incomeRows = 0;
        for (String[] alias : aliases) {
            transactionRows += repairAlias("portfolio_transaction", "ticker", providerType, alias[0], alias[1]);
            realizedRows += repairAlias("portfolio_realized_lot", "symbol", providerType, alias[0], alias[1]);
            incomeRows += repairAlias("portfolio_income", "symbol", providerType, alias[0], alias[1]);
        }
        return new RepairCounts(transactionRows, realizedRows, incomeRows);
    }

    private int repairAlias(String tableName, String columnName, String providerType, String source, String target) {
        return jdbcTemplate.update("update marketbot." + tableName
                        + " set " + columnName + " = ? where provider_type = ? and " + columnName + " = ?",
                target, providerType, source);
    }

    private record RepairCounts(int transactionRows, int realizedRows, int incomeRows) {
    }
}
