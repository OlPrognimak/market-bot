#!/usr/bin/env bash
set -euo pipefail

# Restores a PostgreSQL custom-format dump into the PostgreSQL 17 TimescaleDB target.
# The script verifies the target major version before running pg_restore.

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 work/dumps/<database>_<timestamp>.dump" >&2
  exit 1
fi

DUMP_FILE="$1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
TARGET_DATASOURCE_URL="${TARGET_DATASOURCE_URL:-jdbc:postgresql://localhost:3477/test_db}"
TARGET_USERNAME="${TARGET_USERNAME:-${APP_DATASOURCE_USERNAME:-test}}"
TARGET_PASSWORD="${TARGET_PASSWORD:-${APP_DATASOURCE_PASSWORD:-test}}"
TARGET_COMPOSE_FILE="${TARGET_COMPOSE_FILE:-${PROJECT_ROOT}/compose.pg17-timescale.yaml}"
TARGET_COMPOSE_SERVICE="${TARGET_COMPOSE_SERVICE:-postgres17-timescale}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command is missing: $1" >&2
    exit 1
  fi
}

has_command() {
  command -v "$1" >/dev/null 2>&1
}

jdbc_to_pg_url() {
  local url="$1"
  if [[ "$url" == jdbc:postgresql://* ]]; then
    echo "${url#jdbc:}"
    return 0
  fi
  if [[ "$url" == postgresql://* || "$url" == postgres://* ]]; then
    echo "$url"
    return 0
  fi

  echo "Unsupported datasource URL. Expected jdbc:postgresql://..., postgresql://..., or postgres://..." >&2
  echo "URL: $url" >&2
  exit 1
}

database_name_from_url() {
  local pg_url="$1"
  local without_query="${pg_url%%\?*}"
  local name="${without_query##*/}"
  if [[ -z "$name" || "$name" == "$without_query" ]]; then
    echo "Could not infer database name from URL: $pg_url" >&2
    exit 1
  fi
  echo "$name"
}

if [[ ! -f "$DUMP_FILE" ]]; then
  echo "Dump file does not exist: $DUMP_FILE" >&2
  exit 1
fi

TARGET_PG_URL="$(jdbc_to_pg_url "$TARGET_DATASOURCE_URL")"
TARGET_DB_NAME="$(database_name_from_url "$TARGET_PG_URL")"

if has_command psql && has_command pg_restore; then
  RESTORE_MODE="local"
  SERVER_VERSION_NUM="$(
    PGPASSWORD="$TARGET_PASSWORD" psql \
      --no-psqlrc \
      --tuples-only \
      --no-align \
      --username="$TARGET_USERNAME" \
      --dbname="$TARGET_PG_URL" \
      --command="SELECT current_setting('server_version_num');"
  )"
elif has_command docker; then
  RESTORE_MODE="docker-compose"
  SERVER_VERSION_NUM="$(
    docker compose -f "$TARGET_COMPOSE_FILE" exec -T "$TARGET_COMPOSE_SERVICE" \
      env "PGPASSWORD=$TARGET_PASSWORD" \
      psql \
        --no-psqlrc \
        --tuples-only \
        --no-align \
        --username="$TARGET_USERNAME" \
        --dbname="$TARGET_DB_NAME" \
        --command="SELECT current_setting('server_version_num');"
  )"
else
  cat >&2 <<EOF
Required commands are missing: pg_restore and psql

Install PostgreSQL client tools, or run Docker Compose so this script can use
pg_restore and psql inside the PostgreSQL 17 TimescaleDB container.
EOF
  exit 1
fi
SERVER_VERSION_NUM="${SERVER_VERSION_NUM//[[:space:]]/}"

if [[ -z "$SERVER_VERSION_NUM" || "$SERVER_VERSION_NUM" -lt 170000 ]]; then
  echo "Restore target is not PostgreSQL 17 or newer." >&2
  echo "Target URL: $TARGET_DATASOURCE_URL" >&2
  echo "server_version_num: ${SERVER_VERSION_NUM:-unknown}" >&2
  exit 1
fi

cat <<EOF
Restoring dump into PostgreSQL 17 target.

Target URL:  $TARGET_DATASOURCE_URL
Target user: $TARGET_USERNAME
Dump file:   $DUMP_FILE

This restore uses --clean --if-exists against the target database only.
EOF

if [[ "$RESTORE_MODE" == "local" ]]; then
  PGPASSWORD="$TARGET_PASSWORD" pg_restore \
    --verbose \
    --exit-on-error \
    --clean \
    --if-exists \
    --no-owner \
    --username="$TARGET_USERNAME" \
    --dbname="$TARGET_PG_URL" \
    "$DUMP_FILE"
else
  echo "Local pg_restore/psql are not available. Using Docker Compose service: $TARGET_COMPOSE_SERVICE"
  docker compose -f "$TARGET_COMPOSE_FILE" exec -T "$TARGET_COMPOSE_SERVICE" \
    env "PGPASSWORD=$TARGET_PASSWORD" \
    pg_restore \
      --verbose \
      --exit-on-error \
      --clean \
      --if-exists \
      --no-owner \
      --username="$TARGET_USERNAME" \
      --dbname="$TARGET_DB_NAME" < "$DUMP_FILE"
fi

echo "Restore completed."
