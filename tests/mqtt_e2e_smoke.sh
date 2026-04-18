#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OPS_DIR="${ROOT_DIR}/ops"

TURNOUT_ID="${TURNOUT_ID:-turnout1}"
TOPIC="${TOPIC:-jmri/intent/turnout/${TURNOUT_ID}}"
BROKER_HOST="${BROKER_HOST:-localhost}"
MYSQL_DB="${MYSQL_DB:-train_controller}"
MYSQL_USER="${MYSQL_USER:-train}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-train}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-20}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-1}"
INTERCEPTOR_HEALTH_URL="${INTERCEPTOR_HEALTH_URL:-http://localhost:8080/actuator/health}"

require_cmd() {
    local cmd="$1"
    if ! command -v "$cmd" >/dev/null 2>&1; then
        echo "ERROR: required command not found: $cmd" >&2
        exit 1
    fi
}

mysql_query() {
    local sql="$1"
    docker exec -i "$MYSQL_CONTAINER_ID" mysql -N -B -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" -e "$sql"
}

require_cmd docker
require_cmd mosquitto_pub

MYSQL_CONTAINER_ID="$(cd "$OPS_DIR" && docker compose ps -q mysql)"
if [[ -z "$MYSQL_CONTAINER_ID" ]]; then
    echo "ERROR: mysql container is not running. Start dependencies from ops/ first." >&2
    exit 1
fi

if command -v curl >/dev/null 2>&1; then
    health_payload="$(curl -sS -m 2 "$INTERCEPTOR_HEALTH_URL" || true)"
    if [[ "$health_payload" != *'"status":"UP"'* ]]; then
        echo "ERROR: interceptor health check failed at ${INTERCEPTOR_HEALTH_URL}" >&2
        echo "Hint: start interceptor-java before running this smoke script." >&2
        exit 1
    fi
fi

command_id="cmd-smoke-$(date +%Y%m%d%H%M%S)"
correlation_id="corr-smoke-${command_id}"
desired_state="OPEN"

payload=$(cat <<JSON
{"commandId":"${command_id}","correlationId":"${correlation_id}","turnoutId":"${TURNOUT_ID}","desiredState":"${desired_state}"}
JSON
)

echo "Publishing smoke command"
echo "  topic: ${TOPIC}"
echo "  command_id: ${command_id}"

mosquitto_pub -h "$BROKER_HOST" -t "$TOPIC" -m "$payload"

deadline=$(( $(date +%s) + TIMEOUT_SECONDS ))
while (( $(date +%s) <= deadline )); do
    status="$(mysql_query "SELECT command_status FROM tc_command WHERE command_id='${command_id}' LIMIT 1;" | tr -d '\r')"
    if [[ -n "$status" ]]; then
        echo "Current command_status: $status"
    fi

    if [[ "$status" == "VERIFIED" ]]; then
        state_row="$(mysql_query "SELECT desired_state, actual_state, state_quality FROM device_state WHERE device_id='${TURNOUT_ID}' LIMIT 1;" | tr -d '\r')"
        actual_desired="$(echo "$state_row" | awk '{print $1}')"
        actual_state="$(echo "$state_row" | awk '{print $2}')"
        quality="$(echo "$state_row" | awk '{print $3}')"

        if [[ "$actual_desired" == "$desired_state" && "$actual_state" == "$desired_state" && "$quality" == "GOOD" ]]; then
            echo "PASS: command VERIFIED and device_state=${actual_desired}/${actual_state} quality=${quality}"
            exit 0
        fi

        echo "ERROR: command VERIFIED but device_state mismatch (${state_row})" >&2
        exit 1
    fi

    sleep "$POLL_INTERVAL_SECONDS"
done

last_status="$(mysql_query "SELECT command_status, failure_reason FROM tc_command WHERE command_id='${command_id}' LIMIT 1;" | tr -d '\r')"
echo "ERROR: timed out waiting for VERIFIED. Last status: ${last_status:-<none>}" >&2
echo "Hint: ensure interceptor-java is running and serial adapter is available." >&2
exit 1
