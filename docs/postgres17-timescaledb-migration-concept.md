# PostgreSQL 17 and TimescaleDB Migration Concept

## Goal

Migrate Market Bot from PostgreSQL 14 to PostgreSQL 17 and prepare selected high-volume time-series tables for TimescaleDB.

The migration should be conservative:

- keep the PostgreSQL 14 data volume intact
- create a new PostgreSQL 17 / TimescaleDB volume
- use a `pg_dump -Fc` backup and `pg_restore`
- keep existing Java entity names, field names, API contracts, and UI behavior stable
- introduce TimescaleDB only where the current schema and query patterns support it safely

## Current State

### Runtime and Database Configuration

The project uses Spring Boot, Spring Data JPA, PostgreSQL, Docker Compose, and a Next.js frontend.

Current database configuration:

- `application.yaml` points to `jdbc:postgresql://localhost:5455/test_db` by default.
- `compose.yaml` starts only the `market-bot` application container.
- The app container uses `APP_DATASOURCE_URL`, defaulting to `jdbc:postgresql://host.docker.internal:5455/test_db`.
- There is currently no PostgreSQL service or named database volume in `compose.yaml`.
- `.env.example` exposes `APP_DATASOURCE_*` and Hibernate schema settings.

Current schema management:

- The app uses `spring.jpa.hibernate.ddl-auto: update`.
- Hibernate default schema is `marketbot`.
- There is no Liquibase dependency in `pom.xml`.
- There is no `src/main/resources/db/changelog` directory.

This means the prompt's Liquibase requirement cannot be implemented directly without first introducing Liquibase as a new schema-management layer.

## Recommended Migration Strategy

### Phase 1: Add Explicit PostgreSQL 17 / TimescaleDB Runtime

Add a database service to `compose.yaml` or create a separate Compose file such as `compose.pg17-timescale.yaml`.

Recommended image:

```yaml
image: timescale/timescaledb:latest-pg17
```

Recommended service shape:

```yaml
services:
  postgres17:
    image: timescale/timescaledb:latest-pg17
    restart: unless-stopped
    ports:
      - "${POSTGRES_PORT:-5455}:5432"
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-test_db}
      POSTGRES_USER: ${APP_DATASOURCE_USERNAME:-test}
      POSTGRES_PASSWORD: ${APP_DATASOURCE_PASSWORD:-test}
    volumes:
      - market_bot_pg17_data:/var/lib/postgresql/data
      - ./work/dumps:/work/dumps

volumes:
  market_bot_pg17_data:
```

Do not reuse an existing PostgreSQL 14 volume. Keep any current host database or old Docker volume untouched until the PostgreSQL 17 restore and application verification are complete.

### Phase 2: Backup PostgreSQL 14

Create `scripts/db_dump_pg14.sh`.

Concept:

```bash
#!/usr/bin/env bash
set -euo pipefail

mkdir -p work/dumps

DB_NAME="${POSTGRES_DB:-test_db}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
DUMP_FILE="work/dumps/${DB_NAME}_${TIMESTAMP}.dump"

pg_dump \
  --format=custom \
  --file="${DUMP_FILE}" \
  "${APP_DATASOURCE_URL}"
```

If dumping from a Dockerized PostgreSQL 14 container, run `pg_dump -Fc` inside that container and write the dump into `/work/dumps`.

### Phase 3: Restore into PostgreSQL 17

Create `scripts/db_restore_pg17.sh`.

Concept:

```bash
#!/usr/bin/env bash
set -euo pipefail

DUMP_FILE="${1:?Usage: scripts/db_restore_pg17.sh work/dumps/<dump-file>.dump}"

pg_restore \
  --clean \
  --if-exists \
  --no-owner \
  --dbname="${APP_DATASOURCE_URL}" \
  "${DUMP_FILE}"
```

Run this only against the new PostgreSQL 17 database. The script should fail fast if the target connection is not the PostgreSQL 17 container.

### Phase 4: Introduce Liquibase

Before adding TimescaleDB changesets, decide whether this project should move from Hibernate-managed schema updates to Liquibase-managed schema updates.

Recommended first step:

- add `spring-boot-starter-liquibase`
- add `src/main/resources/db/changelog/db.changelog-master.yaml`
- set `spring.jpa.hibernate.ddl-auto=validate` after the baseline is proven
- create a baseline changelog from the current schema, or mark the current schema as already applied using Liquibase baseline procedures

Avoid running Hibernate `ddl-auto=update` and Liquibase structural migrations independently long term. That creates schema drift and makes TimescaleDB constraint handling harder to reason about.

## TimescaleDB Table Analysis

The current table names below use Hibernate's physical naming convention. Confirm against the real database before writing final changesets.

### Convert First Candidates

These are the strongest candidates from the current app because they store repeated quote samples and the repositories query by symbol plus time.

| Entity | Expected table | Time column | Query pattern | Recommendation |
| --- | --- | --- | --- | --- |
| `QuoteEntity` | `quote_entity` | `created` | symbol + created range/order | Candidate, but primary key must be handled first |
| `CryptoQuoteEntity` | `crypto_quote` | `created` | symbol + created range/order | Candidate, but primary key must be handled first |
| `ShareSessionQuoteEntity` | `share_session_quote` | `provider_timestamp` | symbol/session + provider timestamp | Candidate, but primary key must be handled first |
| `FuturesQuoteEntity` | `futures_quote` | `provider_timestamp` | symbol + provider timestamp | Candidate, but primary key must be handled first |

Useful indexes:

```sql
CREATE INDEX IF NOT EXISTS idx_quote_entity_symbol_created_desc
    ON marketbot.quote_entity (symbol, created DESC);

CREATE INDEX IF NOT EXISTS idx_quote_entity_created_desc
    ON marketbot.quote_entity (created DESC);

CREATE INDEX IF NOT EXISTS idx_crypto_quote_symbol_created_desc
    ON marketbot.crypto_quote (symbol, created DESC);

CREATE INDEX IF NOT EXISTS idx_share_session_quote_symbol_session_time_desc
    ON marketbot.share_session_quote (symbol, session_type, provider_timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_futures_quote_symbol_time_desc
    ON marketbot.futures_quote (symbol, provider_timestamp DESC);
```

### Defer or Review Carefully

| Entity | Expected table | Reason to defer |
| --- | --- | --- |
| `HistoryQuoteEntity` | `history_quote_entity` | Uses `LocalDate trading_date` and has unique constraint `(symbol, trading_date, interval_type)`. This can work only if TimescaleDB accepts the chosen date partition column and every unique constraint includes it. Validate with real DDL first. |
| `TradeExecutionEntity` | `trade_execution` | Time-series-like order history, but lower volume and not market data. Convert only if trading volume grows. |
| `TradeOrderEntity` | `trade_order` | Operational workflow table with mutable statuses and expiry handling. Keep relational. |
| `TradeRiskEventEntity` | `trade_risk_event` | Event-like, but no explicit event timestamp other than inherited `created`. Keep relational for now. |
| `NewsArticleEntity` | `news_article` | Has `published_at`, but unique constraints on provider id and content hash do not include time. Not safe for hypertable conversion without constraint redesign. |
| `NewsInsightEntity` | `news_insight` | Has `analyzed_at`, but unique constraint is article/instrument/symbol. Keep relational. |
| `PortfolioTransactionEntity` | `portfolio_transaction` | Has `event_time`, but unique import fingerprint does not include time. Keep relational. |

## Critical TimescaleDB Constraint Issue

TimescaleDB requires every primary key and unique constraint on a hypertable to include the partitioning time column.

The current candidate entities all use:

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

That produces a primary key on `id` only. A hypertable on `created` or `provider_timestamp` would therefore be incompatible unless the primary key is changed.

Practical options:

1. Keep these tables as regular PostgreSQL tables for the first PostgreSQL 17 migration, then plan a separate Timescale schema migration.
2. Convert candidate tables to composite primary keys such as `(id, created)` or `(id, provider_timestamp)`, and update Java/JPA expectations if needed.
3. Remove the primary key and use non-unique indexes, which is not recommended for JPA-managed entities.
4. Create new Timescale append-only tables for quotes and write new samples there while keeping current JPA tables stable during a transition period.

Recommended path: migrate PostgreSQL 14 to PostgreSQL 17 first, introduce Liquibase baseline second, then convert the quote tables in a dedicated migration after testing JPA behavior with composite hypertable-compatible keys.

## Liquibase Changeset Concept

After Liquibase exists, add a TimescaleDB changelog such as:

```yaml
databaseChangeLog:
  - changeSet:
      id: 2026-07-09-001-enable-timescaledb
      author: market-bot
      preConditions:
        onFail: MARK_RAN
        dbms:
          type: postgresql
      changes:
        - sql:
            sql: CREATE EXTENSION IF NOT EXISTS timescaledb;
```

For each selected hypertable, use guarded SQL and comments:

```sql
-- quote_entity stores regular-session share quote samples.
-- Time column: created.
-- Requires primary/unique constraints to include created before activation.
SELECT create_hypertable(
    'marketbot.quote_entity',
    'created',
    if_not_exists => TRUE
);
```

Do not add `create_hypertable` changesets until the primary-key and unique-index compatibility is proven on a restored PostgreSQL 17 copy.

## Java Backend Changes Needed

### Dependencies and Configuration

Required if Liquibase is adopted:

- add `spring-boot-starter-liquibase`
- add Liquibase master changelog config
- change deployment defaults from `ddl-auto=update` toward `ddl-auto=validate`
- keep `org.hibernate.dialect.PostgreSQLDialect`; no special Timescale dialect is required

### Entity and Repository Impact

If existing quote tables are converted in place:

- `QuoteEntity`, `CryptoQuoteEntity`, `ShareSessionQuoteEntity`, and `FuturesQuoteEntity` may need primary key changes to satisfy TimescaleDB.
- JPA repositories currently assume `Long id` primary keys. Composite keys would require `@EmbeddedId`, `@IdClass`, or a different persistence approach.
- Existing generated identity columns may remain useful, but they cannot be the only unique key on a hypertable.
- Existing repository methods are compatible with TimescaleDB query-wise because they filter/order by the same time columns that should become partition columns.

Safer Java alternative:

- keep current JPA entities unchanged
- create separate append-only hypertable entities or JDBC write/read paths for historical quote samples
- gradually move dashboard chart queries to the Timescale tables

### Schema Repair Classes

`AppUserPropertySchemaRepair` and `PortfolioSchemaRepair` use `JdbcTemplate` for targeted schema/data repair. They should be reviewed before Liquibase rollout because they are a second source of schema mutation outside migrations.

Recommended direction:

- keep them temporarily for data repair only
- move structural repairs into Liquibase changesets
- avoid schema-altering `JdbcTemplate` code once Liquibase becomes authoritative

### Tests

Add integration tests or a manual verification profile that starts PostgreSQL 17 / TimescaleDB and validates:

- application startup
- Liquibase changelog execution
- `CREATE EXTENSION timescaledb`
- quote insert/read flows
- chart range queries
- scanner persistence
- restore from a PostgreSQL 14 dump

Existing service tests are useful but do not validate PostgreSQL 17, TimescaleDB extension availability, or hypertable constraints.

## UI Changes Needed

No immediate UI changes are required for the database engine migration if API contracts remain stable.

Recommended optional admin UI additions:

- a system health panel showing database version
- TimescaleDB extension status
- hypertable list/count
- latest quote timestamps per data domain
- migration/readiness status for `quote_entity`, `crypto_quote`, `share_session_quote`, and `futures_quote`

Existing frontend areas affected indirectly:

- Shares dashboard charts depend on `QuoteRepository` range queries.
- Crypto charts depend on `CryptoQuoteRepository` range queries.
- Extended-hours and futures dashboards depend on `provider_timestamp` order/range queries.
- Portfolio analysis reads latest regular quote prices through `QuoteRepository`.
- Trading preview/risk checks read latest regular quote prices.

If table names, API responses, or repository behavior stay unchanged, these UI screens should need no functional changes.

## Migration Documentation Needed

Create `scripts/db_migrate_pg14_to_pg17.md` with:

1. how to identify the current PostgreSQL 14 source
2. how to create `work/dumps`
3. how to run `pg_dump -Fc`
4. how to start the PostgreSQL 17 / TimescaleDB container with a new volume
5. how to restore with `pg_restore`
6. how to run the Spring Boot app
7. how to verify database version and TimescaleDB

Verification SQL:

```sql
SELECT version();
SELECT extname FROM pg_extension WHERE extname = 'timescaledb';
SELECT * FROM timescaledb_information.hypertables;
```

## Risks

### High Risk

- TimescaleDB hypertable conversion can fail because current primary keys are `id` only and do not include the time column.
- Introducing Liquibase into a Hibernate-managed schema can cause drift if `ddl-auto=update` remains enabled.
- Reusing the old PostgreSQL 14 data directory with PostgreSQL 17 would be unsafe. Use dump/restore only.

### Medium Risk

- The app currently expects an external host database; adding a Compose database service changes local deployment behavior and environment defaults.
- Existing schema was generated by Hibernate, so real table names, constraint names, and indexes must be inspected before writing final Liquibase SQL.
- Large `pg_restore` operations may require downtime and enough local disk space for both old volume, dump, and new volume.
- `latest-pg17` is mutable. Pin a concrete TimescaleDB image tag before production use.

### Low Risk

- Frontend changes are likely unnecessary if backend APIs remain stable.
- PostgreSQL JDBC driver and Hibernate PostgreSQL dialect should continue to work with PostgreSQL 17.
- Existing repository query shapes align well with the proposed time-series indexes.

## Recommended Implementation Order

1. Add PostgreSQL 17 / TimescaleDB Compose service with a new named volume.
2. Add dump and restore scripts plus migration documentation.
3. Restore a PostgreSQL 14 dump into PostgreSQL 17 and run the app unchanged.
4. Introduce Liquibase baseline while keeping schema behavior equivalent.
5. Enable TimescaleDB extension only.
6. Inspect real database constraints and table names after restore.
7. Decide whether to convert existing quote tables in place or create new append-only Timescale tables.
8. Add hypertable conversions only after primary-key compatibility is solved and tested.

## Initial Recommendation

Do not convert hypertables in the same change that upgrades PostgreSQL 14 to PostgreSQL 17.

First complete a safe engine migration with dump/restore and no schema semantics change. Then introduce Liquibase and TimescaleDB extension. Convert quote/history tables only in a later migration once the primary-key strategy is explicit and validated against the restored database.
