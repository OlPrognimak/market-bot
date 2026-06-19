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
        transactionRows += jdbcTemplate.update("""
                update marketbot.portfolio_transaction
                   set ticker = 'MRK.DE'
                 where provider_type = 'TRADE_REPUBLIC'
                   and ticker = 'DE0006599905'
                """);
        transactionRows += jdbcTemplate.update("""
                update marketbot.portfolio_transaction
                   set ticker = 'BAC'
                 where provider_type = 'TRADE_REPUBLIC'
                   and ticker = 'US0605051046'
                """);
        transactionRows += jdbcTemplate.update("""
                update marketbot.portfolio_transaction
                   set ticker = 'JPM'
                 where provider_type = 'TRADE_REPUBLIC'
                   and ticker = 'US46625H1005'
                """);
        transactionRows += jdbcTemplate.update("""
                update marketbot.portfolio_transaction
                   set ticker = 'RMBS'
                 where provider_type = 'TRADE_REPUBLIC'
                   and ticker = 'US7509171069'
                """);
        transactionRows += jdbcTemplate.update("""
                update marketbot.portfolio_transaction
                   set ticker = 'SPCX'
                 where provider_type = 'TRADE_REPUBLIC'
                   and ticker = 'US84615Q1031'
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
        realizedRows += jdbcTemplate.update("""
                update marketbot.portfolio_realized_lot
                   set symbol = 'MRK.DE'
                 where provider_type = 'TRADE_REPUBLIC'
                   and symbol = 'DE0006599905'
                """);
        realizedRows += jdbcTemplate.update("""
                update marketbot.portfolio_realized_lot
                   set symbol = 'BAC'
                 where provider_type = 'TRADE_REPUBLIC'
                   and symbol = 'US0605051046'
                """);
        realizedRows += jdbcTemplate.update("""
                update marketbot.portfolio_realized_lot
                   set symbol = 'JPM'
                 where provider_type = 'TRADE_REPUBLIC'
                   and symbol = 'US46625H1005'
                """);
        realizedRows += jdbcTemplate.update("""
                update marketbot.portfolio_realized_lot
                   set symbol = 'RMBS'
                 where provider_type = 'TRADE_REPUBLIC'
                   and symbol = 'US7509171069'
                """);
        realizedRows += jdbcTemplate.update("""
                update marketbot.portfolio_realized_lot
                   set symbol = 'SPCX'
                 where provider_type = 'TRADE_REPUBLIC'
                   and symbol = 'US84615Q1031'
                """);
        log.info("Repaired Trade Republic aliases: {} transactions, {} realized lots.",
                transactionRows, realizedRows);
    }
}
