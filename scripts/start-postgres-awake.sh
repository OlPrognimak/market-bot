#!/usr/bin/env bash
set -euo pipefail

# Keeps macOS awake while your existing local PostgreSQL Docker container is running.
# Stop this script with Ctrl+C when you no longer need sleep prevention.
# The PostgreSQL container itself is not stopped by this script.

CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-docker-postgres-1}"
DOCKER_START_TIMEOUT_SECONDS="${DOCKER_START_TIMEOUT_SECONDS:-300}"
DOCKER_APP_PATH="${DOCKER_APP_PATH:-/Applications/Docker.app}"
DOCKER_APP_BINARY="${DOCKER_APP_PATH}/Contents/MacOS/Docker"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command is missing: $1" >&2
    exit 1
  fi
}

wait_for_docker() {
  local attempts=$((DOCKER_START_TIMEOUT_SECONDS / 2))
  local delay_seconds=2
  if ((attempts < 1)); then
    attempts=1
  fi

  for ((i = 1; i <= attempts; i++)); do
    if docker info >/dev/null 2>&1; then
      return 0
    fi
    if ((i % 5 == 0)); then
      echo "Waiting for Docker Desktop... $((i * delay_seconds))/${DOCKER_START_TIMEOUT_SECONDS}s"
    fi
    sleep "$delay_seconds"
  done

  echo "Docker did not become ready after $((attempts * delay_seconds)) seconds." >&2
  echo "Open Docker Desktop manually and wait until it shows 'Docker Desktop is running', then run this configuration again." >&2
  exit 1
}

start_docker_desktop() {
  if docker info >/dev/null 2>&1; then
    return 0
  fi

  if [[ ! -d "$DOCKER_APP_PATH" ]]; then
    echo "Docker is not running and $DOCKER_APP_PATH was not found." >&2
    exit 1
  fi

  echo "Starting Docker Desktop from $DOCKER_APP_PATH..."
  open "$DOCKER_APP_PATH" || true
  sleep 10

  if ! pgrep -f "$DOCKER_APP_BINARY" >/dev/null 2>&1 && [[ -x "$DOCKER_APP_BINARY" ]]; then
    echo "Docker Desktop process was not detected after open. Starting app executable directly..."
    nohup "$DOCKER_APP_BINARY" >/tmp/market-bot-docker-desktop.log 2>&1 &
    sleep 10
  fi

  wait_for_docker
}

start_postgres() {
  if docker ps --format '{{.Names}}' | grep -Fxq "$CONTAINER_NAME"; then
    echo "PostgreSQL container is already running: $CONTAINER_NAME"
    return 0
  fi

  if docker ps -a --format '{{.Names}}' | grep -Fxq "$CONTAINER_NAME"; then
    echo "Starting existing PostgreSQL container: $CONTAINER_NAME"
    docker start "$CONTAINER_NAME" >/dev/null
    return 0
  fi

  cat >&2 <<EOF
PostgreSQL container '$CONTAINER_NAME' does not exist.

This script is configured to use an existing container and will not create a new one.

Check existing containers:
  docker ps -a

Available containers now:
$(docker ps -a --format '  {{.Names}}' 2>/dev/null || true)

Use another container name:
  POSTGRES_CONTAINER_NAME=your-container ./scripts/start-postgres-awake.sh
EOF
  exit 1
}

print_status() {
  cat <<EOF

PostgreSQL is running in Docker.

Container:  $CONTAINER_NAME

macOS sleep prevention is active while this script is running.
Press Ctrl+C to stop sleep prevention. The PostgreSQL container will continue running.

To stop PostgreSQL manually:
  docker stop $CONTAINER_NAME

To remove the container but keep data:
  docker rm $CONTAINER_NAME

EOF
}

require_command docker
require_command open
require_command caffeinate
require_command pgrep

start_docker_desktop
start_postgres
print_status

caffeinate -dimsu
