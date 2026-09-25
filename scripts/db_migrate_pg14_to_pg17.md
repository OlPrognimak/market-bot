# PostgreSQL 14 to PostgreSQL 17 / TimescaleDB Migration Runbook

## Purpose

Move the Market Bot database from PostgreSQL 14 to a new PostgreSQL 17 / TimescaleDB container without reusing the old data directory or deleting the old database.

This runbook uses:

- `compose.pg17-timescale.yaml`
- `scripts/db_dump_pg14.sh`
- `scripts/db_restore_pg17.sh`

## Safety Rules

- Do not mount a PostgreSQL 14 data directory into the PostgreSQL 17 container.
- Do not delete old Docker volumes during the migration.
- Do not point the restore script at the old PostgreSQL 14 database.
- Keep the dump file until the application has been verified against PostgreSQL 17.
- Do not enable TimescaleDB hypertable conversion until the primary-key strategy has been reviewed.

## Default Connections

Current PostgreSQL 14 source default:

```text
jdbc:postgresql://localhost:5455/test_db
```

New PostgreSQL 17 / TimescaleDB target default:

```text
jdbc:postgresql://localhost:3477/test_db
```

Default credentials:

```text
username: test
password: test
```

Override these with environment variables when needed:

```bash
APP_DATASOURCE_USERNAME=...
APP_DATASOURCE_PASSWORD=...
SOURCE_DATASOURCE_URL=jdbc:postgresql://localhost:5455/test_db
TARGET_DATASOURCE_URL=jdbc:postgresql://localhost:3477/test_db
```

## 1. Create a PostgreSQL 14 Dump

Make sure the old PostgreSQL 14 database is running and reachable.

Run:

```bash
./scripts/db_dump_pg14.sh
```

Prefer running the script without `sudo`. If Docker access on your machine requires `sudo`, the script still writes to `/Users/alexadmin/Desktop/work/backups`.

The script writes a custom-format dump to:

```text
/Users/alexadmin/Desktop/work/backups/<database>_<YYYYMMDD_HHMMSS>.dump
```

Override the location with `DUMP_DIR` or `BACKUP_DIR`:

```bash
BACKUP_DIR=/Users/alexadmin/Desktop/work/backups ./scripts/db_dump_pg14.sh
```

For a non-default source:

```bash
SOURCE_DATASOURCE_URL=jdbc:postgresql://localhost:5455/test_db \
APP_DATASOURCE_USERNAME=test \
APP_DATASOURCE_PASSWORD=test \
./scripts/db_dump_pg14.sh
```

If `pg_dump` is not installed locally, the script can use `pg_dump` from an existing PostgreSQL Docker container:

```bash
SOURCE_POSTGRES_CONTAINER=<postgres14-container-name> \
APP_DATASOURCE_USERNAME=test \
APP_DATASOURCE_PASSWORD=test \
./scripts/db_dump_pg14.sh
```

Find container names with:

```bash
docker ps --format '{{.Names}}'
```

If `pg_dump` is not installed locally and no source container is provided, the script falls back to a temporary Docker client container:

```bash
docker run --rm postgres:14 pg_dump ...
```

For the default source URL, the Docker fallback connects to `host.docker.internal:5455/test_db` and writes the dump into `/Users/alexadmin/Desktop/work/backups`.

Override the client image if needed:

```bash
DUMP_CLIENT_IMAGE=postgres:14 ./scripts/db_dump_pg14.sh
```

## 2. Start PostgreSQL 17 / TimescaleDB

Start the separate database Compose file:

```bash
docker compose -f compose.pg17-timescale.yaml up -d
```

Check status:

```bash
docker compose -f compose.pg17-timescale.yaml ps
```

The container exposes PostgreSQL on host port `3477` by default.

The data is stored in a new named volume:

```text
market_bot_pg17_timescale_data
```

## 3. Restore the Dump into PostgreSQL 17

Run:

```bash
./scripts/db_restore_pg17.sh /Users/alexadmin/Desktop/work/backups/<database>_<YYYYMMDD_HHMMSS>.dump
```

The restore script resolves `compose.pg17-timescale.yaml` from the project root, so it can also be run from inside the `scripts/` directory:

```bash
./db_restore_pg17.sh /Users/alexadmin/Desktop/work/backups/<database>_<YYYYMMDD_HHMMSS>.dump
```

For a non-default target:

```bash
TARGET_DATASOURCE_URL=jdbc:postgresql://localhost:3477/test_db \
APP_DATASOURCE_USERNAME=test \
APP_DATASOURCE_PASSWORD=test \
./scripts/db_restore_pg17.sh /Users/alexadmin/Desktop/work/backups/<database>_<YYYYMMDD_HHMMSS>.dump
```

The restore script checks `server_version_num` and fails if the target is not PostgreSQL 17 or newer.

If `psql` or `pg_restore` is not installed locally, the script automatically uses the PostgreSQL client tools inside the Compose service from `compose.pg17-timescale.yaml`.

Override the Compose target if needed:

```bash
TARGET_COMPOSE_FILE=compose.pg17-timescale.yaml \
TARGET_COMPOSE_SERVICE=postgres17-timescale \
./scripts/db_restore_pg17.sh /Users/alexadmin/Desktop/work/backups/<database>_<YYYYMMDD_HHMMSS>.dump
```

## 4. Point the App at PostgreSQL 17

For local backend runs:

```bash
APP_DATASOURCE_URL=jdbc:postgresql://localhost:3477/test_db
```

For the Docker app container on macOS:

```bash
APP_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:3477/test_db
```

Then start the backend as usual:

```bash
./mvnw spring-boot:run
```

Or with the existing app Compose flow:

```bash
docker compose --env-file .env up --build -d
```

## 5. Verify PostgreSQL and TimescaleDB

Connect with `psql`:

```bash
PGPASSWORD=test psql -h localhost -p 3477 -U test -d test_db
```

Run:

```sql
SELECT version();
SELECT extname FROM pg_extension WHERE extname = 'timescaledb';
SELECT * FROM timescaledb_information.hypertables;
```

At this stage, `timescaledb` may not be enabled yet if no Liquibase or manual extension step has been applied. An empty hypertable list is expected before hypertable migration.

## 6. Application Verification

After the app starts against PostgreSQL 17, verify:

- login works
- Shares dashboard loads
- Crypto dashboard loads
- Futures dashboard loads
- chart dialogs load quote history
- portfolio analysis can read latest quote prices
- paper trading preview can read latest quote prices

## Rollback

Rollback means pointing the app back to the old PostgreSQL 14 database:

```bash
APP_DATASOURCE_URL=jdbc:postgresql://localhost:5455/test_db
```

Do not remove the PostgreSQL 17 volume until the migration has been accepted.

Stop the PostgreSQL 17 container without deleting data:

```bash
docker compose -f compose.pg17-timescale.yaml stop
```
