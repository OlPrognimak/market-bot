#!/usr/bin/env bash
set -euo pipefail

# Creates a PostgreSQL custom-format dump from the PostgreSQL 14 source DB.
# Defaults match the current local application database configuration.

SOURCE_DATASOURCE_URL="${SOURCE_DATASOURCE_URL:-${APP_DATASOURCE_URL:-jdbc:postgresql://localhost:5455/test_db}}"
SOURCE_USERNAME="${SOURCE_USERNAME:-${APP_DATASOURCE_USERNAME:-test}}"
SOURCE_PASSWORD="${SOURCE_PASSWORD:-${APP_DATASOURCE_PASSWORD:-test}}"
SOURCE_POSTGRES_CONTAINER="${SOURCE_POSTGRES_CONTAINER:-${POSTGRES_CONTAINER_NAME:-}}"
DUMP_CLIENT_IMAGE="${DUMP_CLIENT_IMAGE:-postgres:14}"

default_backup_dir() {
  echo "/Users/alexadmin/Desktop/work/backups"
}

DUMP_DIR="${DUMP_DIR:-${BACKUP_DIR:-$(default_backup_dir)}}"
HOST_UID="${SUDO_UID:-$(id -u)}"
HOST_GID="${SUDO_GID:-$(id -g)}"

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

host_from_url() {
  local pg_url="$1"
  local without_scheme="${pg_url#*://}"
  local authority="${without_scheme%%/*}"
  local host_port="${authority##*@}"
  local host="${host_port%%:*}"
  echo "$host"
}

port_from_url() {
  local pg_url="$1"
  local without_scheme="${pg_url#*://}"
  local authority="${without_scheme%%/*}"
  local host_port="${authority##*@}"
  if [[ "$host_port" == *:* ]]; then
    echo "${host_port##*:}"
  else
    echo "5432"
  fi
}

docker_reachable_host() {
  local host="$1"
  if [[ "$host" == "localhost" || "$host" == "127.0.0.1" ]]; then
    echo "host.docker.internal"
  else
    echo "$host"
  fi
}

require_command date
require_command mkdir

SOURCE_PG_URL="$(jdbc_to_pg_url "$SOURCE_DATASOURCE_URL")"
DB_NAME="$(database_name_from_url "$SOURCE_PG_URL")"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
DUMP_FILE="${DUMP_DIR}/${DB_NAME}_${TIMESTAMP}.dump"
DUMP_BASENAME="${DB_NAME}_${TIMESTAMP}.dump"

mkdir -p "$DUMP_DIR"
if [[ -n "${SUDO_USER:-}" && "$SUDO_USER" != "root" ]]; then
  chown "$HOST_UID:$HOST_GID" "$DUMP_DIR"
fi
DUMP_DIR_ABS="$(cd "$DUMP_DIR" && pwd)"

cat <<EOF
Creating PostgreSQL dump.

Source URL:  $SOURCE_DATASOURCE_URL
Source user: $SOURCE_USERNAME
Dump file:   $DUMP_FILE
EOF

if has_command pg_dump; then
  PGPASSWORD="$SOURCE_PASSWORD" pg_dump \
    --format=custom \
    --file="$DUMP_FILE" \
    --username="$SOURCE_USERNAME" \
    --dbname="$SOURCE_PG_URL"
elif [[ -n "$SOURCE_POSTGRES_CONTAINER" ]] && has_command docker; then
  echo "Local pg_dump is not available. Using pg_dump inside Docker container: $SOURCE_POSTGRES_CONTAINER"
  docker exec \
    --env "PGPASSWORD=$SOURCE_PASSWORD" \
    "$SOURCE_POSTGRES_CONTAINER" \
    pg_dump \
      --format=custom \
      --username="$SOURCE_USERNAME" \
      --dbname="$DB_NAME" > "$DUMP_FILE"
elif has_command docker; then
  SOURCE_HOST="$(docker_reachable_host "$(host_from_url "$SOURCE_PG_URL")")"
  SOURCE_PORT="$(port_from_url "$SOURCE_PG_URL")"
  echo "Local pg_dump is not available. Using temporary Docker client image: $DUMP_CLIENT_IMAGE"
  echo "Docker client source: ${SOURCE_HOST}:${SOURCE_PORT}/${DB_NAME}"
  docker run --rm \
    --user "${HOST_UID}:${HOST_GID}" \
    --env "PGPASSWORD=$SOURCE_PASSWORD" \
    --volume "${DUMP_DIR_ABS}:/dumps" \
    "$DUMP_CLIENT_IMAGE" \
    pg_dump \
      --format=custom \
      --file="/dumps/${DUMP_BASENAME}" \
      --host="$SOURCE_HOST" \
      --port="$SOURCE_PORT" \
      --username="$SOURCE_USERNAME" \
      --dbname="$DB_NAME"
else
  cat >&2 <<EOF
Required command is missing: pg_dump

Install PostgreSQL client tools, install Docker, or run through an existing PostgreSQL Docker container:

  SOURCE_POSTGRES_CONTAINER=<postgres14-container-name> $0

Find container names with:

  docker ps --format '{{.Names}}'
EOF
  exit 1
fi

echo "Dump created: $DUMP_FILE"
