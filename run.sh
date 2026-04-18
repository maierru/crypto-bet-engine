#!/bin/bash
set -e

# Crypto Bet Engine — Docker runner
# Opens http://localhost:8080 in browser after startup

APP_PORT=8080
COMPOSE_FILE="docker-compose.yml"

echo "==> Stopping any existing containers..."
docker compose down 2>/dev/null || true

echo "==> Building and starting services..."
docker compose up --build -d

echo "==> Waiting for app to be healthy..."
for i in $(seq 1 60); do
  if curl -sf http://localhost:$APP_PORT/actuator/health > /dev/null 2>&1; then
    echo "==> App is ready!"
    echo "==> Opening http://localhost:$APP_PORT"
    open "http://localhost:$APP_PORT"
    echo ""
    echo "  Logs:  docker compose logs -f app"
    echo "  Stop:  docker compose down"
    exit 0
  fi
  printf "."
  sleep 2
done

echo ""
echo "==> App did not become healthy in time. Check logs:"
docker compose logs app | tail -30
exit 1
