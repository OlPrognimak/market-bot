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
        int transactionRows = jdbcTemplate.update("""
                update marketbot.portfolio_transaction
                   set ticker = 'C8PX.DE'
                 where provider_type = 'TRADE_REPUBLIC'
                   and ticker = 'IE0000ZL1RD2'
                """);
        transactionRows += jdbcTemplate.update("""
                update marketbot.portfolio_transaction
                   set ticker = 'ENR.DE'
                 where provider_type = 'TRADE_REPUBLIC'
                   and ticker in ('US82621A2033', 'SMNEY')
                """);
        int realizedRows = jdbcTemplate.update("""
                update marketbot.portfolio_realized_lot
                   set symbol = 'C8PX.DE'
                 where provider_type = 'TRADE_REPUBLIC'
                   and symbol = 'IE0000ZL1RD2'
                """);
        realizedRows += jdbcTemplate.update("""
                update marketbot.portfolio_realized_lot
                   set symbol = 'ENR.DE'
                 where provider_type = 'TRADE_REPUBLIC'
                   and symbol in ('US82621A2033', 'SMNEY')
                """);
        log.info("Repaired Trade Republic ETF aliases: {} transactions, {} realized lots.",
                transactionRows, realizedRows);
    }
}
