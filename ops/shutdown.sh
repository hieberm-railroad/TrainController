#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Detect docker-compose vs docker compose
if command -v docker-compose &> /dev/null; then
    COMPOSE_CMD="docker-compose"
elif docker compose version &> /dev/null 2>&1; then
    COMPOSE_CMD="docker compose"
else
    echo "[ERROR] docker-compose or 'docker compose' not found."
    exit 1
fi

echo "[INFO] Stopping all services..."
cd "$SCRIPT_DIR"
$COMPOSE_CMD down
echo "[INFO] Services stopped."
