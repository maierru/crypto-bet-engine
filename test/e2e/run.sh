#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_FILE="$PROJECT_DIR/docker-compose.yml"
COMPOSE_E2E="$PROJECT_DIR/docker-compose.e2e.yml"
APP_URL="${APP_URL:-http://localhost:8080}"
HEADLESS="${HEADLESS:-true}"
HEALTH_TIMEOUT=120

cleanup() {
  echo "Stopping docker-compose..."
  docker compose -f "$COMPOSE_FILE" -f "$COMPOSE_E2E" down --volumes --remove-orphans 2>/dev/null || true
}

trap cleanup EXIT

echo "Starting docker-compose with e2e profile..."
docker compose -f "$COMPOSE_FILE" -f "$COMPOSE_E2E" up --build -d

echo "Waiting for app health at $APP_URL/actuator/health (timeout: ${HEALTH_TIMEOUT}s)..."
elapsed=0
until curl -sf "$APP_URL/actuator/health" > /dev/null 2>&1; do
  if [ "$elapsed" -ge "$HEALTH_TIMEOUT" ]; then
    echo "ERROR: App did not become healthy within ${HEALTH_TIMEOUT}s"
    docker compose -f "$COMPOSE_FILE" -f "$COMPOSE_E2E" logs app
    exit 1
  fi
  sleep 2
  elapsed=$((elapsed + 2))
done
echo "App is healthy."

echo "Running E2E tests..."
cd "$SCRIPT_DIR"
bundle install --quiet

export APP_URL HEADLESS
test_exit=0
for test_file in test_*.rb; do
  echo "--- $test_file ---"
  bundle exec ruby "$test_file" || test_exit=$?
done

exit $test_exit
