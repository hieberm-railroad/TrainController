#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
MYSQL_DB="${MYSQL_DB:-train_controller}"
MYSQL_USER="${MYSQL_USER:-train}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-train}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}"
SERVO_DEFAULTS_FILE="${SERVO_DEFAULTS_FILE:-$SCRIPT_DIR/servo-defaults.json}"
INTERCEPTOR_JAR="${INTERCEPTOR_JAR:-$REPO_ROOT/interceptor-java/target/interceptor-java-0.1.0-SNAPSHOT.jar}"
INTERCEPTOR_PID_FILE="${INTERCEPTOR_PID_FILE:-/tmp/traincontroller-interceptor.pid}"

log_info() { echo "[INFO] $*"; }
log_warn() { echo "[WARN] $*" >&2; }
log_error() { echo "[ERROR] $*" >&2; }

require_cmd() {
    if ! command -v "$1" >/dev/null 2>&1; then
        log_error "Required command not found: $1"
        return 1
    fi
}

mysql_root_exec() {
    if mysql -uroot -e "SELECT 1;" >/dev/null 2>&1; then
        mysql -uroot -e "$1"
        return 0
    fi

    if sudo mysql -uroot -e "SELECT 1;" >/dev/null 2>&1; then
        sudo mysql -uroot -e "$1"
        return 0
    fi

    if [[ -n "$MYSQL_ROOT_PASSWORD" ]]; then
        mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "$1"
        return 0
    fi

    log_error "Unable to connect to MySQL as root. Set MYSQL_ROOT_PASSWORD or ensure local unix-socket auth is configured."
    return 1
}

mysql_app_exec() {
    mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" -e "$1"
}

ensure_mysql_running() {
    local service_name=""

    if systemctl is-enabled --quiet mysql 2>/dev/null || systemctl is-active --quiet mysql 2>/dev/null; then
        service_name="mysql"
    elif systemctl is-enabled --quiet mariadb 2>/dev/null || systemctl is-active --quiet mariadb 2>/dev/null; then
        service_name="mariadb"
    fi

    if [[ -z "$service_name" ]]; then
        log_error "Neither mysql nor mariadb is installed or managed by systemd. Install MySQL/MariaDB and rerun this script."
        return 1
    fi

    if ! systemctl is-active --quiet "$service_name"; then
        log_info "Starting $service_name..."
        sudo systemctl start "$service_name"
    fi

    for _ in $(seq 1 30); do
        if mysqladmin ping -h localhost -u root --silent >/dev/null 2>&1 || mysqladmin ping -h localhost -u root -p"$MYSQL_ROOT_PASSWORD" --silent >/dev/null 2>&1; then
            log_info "MySQL is available."
            return 0
        fi
        sleep 1
    done

    log_error "MySQL did not become ready within 30 seconds."
    return 1
}

ensure_database_schema() {
    mysql_root_exec "CREATE DATABASE IF NOT EXISTS $MYSQL_DB;"
    mysql_root_exec "CREATE USER IF NOT EXISTS '$MYSQL_USER'@'localhost' IDENTIFIED BY '$MYSQL_PASSWORD';"
    mysql_root_exec "GRANT ALL PRIVILEGES ON $MYSQL_DB.* TO '$MYSQL_USER'@'localhost';"
    mysql_root_exec "FLUSH PRIVILEGES;"

    for migration in "$REPO_ROOT"/migrations/V*.sql; do
        [[ -f "$migration" ]] || continue
        log_info "Applying migration: $(basename "$migration")"
        mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" < "$migration"
    done
}

ensure_servo_defaults() {
    local row_count
    row_count="$(mysql_app_exec "SELECT COUNT(*) FROM device_servo_config;" 2>/dev/null | tail -n +2 | tr -d ' ' || echo 0)"

    if [[ "$row_count" =~ ^[0-9]+$ ]] && [[ "$row_count" -gt 0 ]]; then
        log_info "Servo configuration already exists; preserving persisted values."
        return 0
    fi

    if [[ ! -f "$SERVO_DEFAULTS_FILE" ]]; then
        log_error "No default servo config file found at $SERVO_DEFAULTS_FILE"
        return 1
    fi

    log_info "No saved servo configuration found; seeding defaults from $SERVO_DEFAULTS_FILE"
    python3 - "$SERVO_DEFAULTS_FILE" "$MYSQL_DB" "$MYSQL_USER" "$MYSQL_PASSWORD" <<'PY'
import json
import subprocess
import sys
from pathlib import Path

config_path = Path(sys.argv[1])
db_name = sys.argv[2]
user = sys.argv[3]
password = sys.argv[4]

with config_path.open("r", encoding="utf-8") as fh:
    rows = json.load(fh)

if not isinstance(rows, list) or not rows:
    raise SystemExit(f"Config file does not contain a non-empty list of servo rows: {config_path}")

values = []
for row in rows:
    values.append(
        "('{device_id}', {open_angle}, {closed_angle})".format(
            device_id=row["device_id"],
            open_angle=row["open_angle"],
            closed_angle=row["closed_angle"],
        )
    )

sql = (
    "INSERT IGNORE INTO device_servo_config (device_id, open_angle, closed_angle) VALUES "
    + ", ".join(values)
    + ";"
)
cmd = [
    "mysql",
    "-u" + user,
    "-p" + password,
    db_name,
    "-e",
    sql,
]
subprocess.run(cmd, check=True)
PY
}

ensure_mosquitto_running() {
    if systemctl is-active --quiet mosquitto; then
        log_info "Mosquitto already running."
        return 0
    fi

    log_info "Starting Mosquitto..."
    sudo systemctl start mosquitto

    for _ in $(seq 1 20); do
        if mosquitto_sub -h localhost -t '$SYS/broker/uptime' -C 1 -W 1 >/dev/null 2>&1; then
            log_info "Mosquitto is available."
            return 0
        fi
        sleep 1
    done

    log_error "Mosquitto did not become ready within 20 seconds."
    return 1
}

ensure_interceptor_running() {
    if [[ -f "$INTERCEPTOR_PID_FILE" ]] && kill -0 "$(cat "$INTERCEPTOR_PID_FILE")" 2>/dev/null; then
        log_info "Interceptor already running."
        return 0
    fi

    if [[ ! -f "$INTERCEPTOR_JAR" ]]; then
        log_warn "Interceptor JAR not found at $INTERCEPTOR_JAR; building it now."
        (cd "$REPO_ROOT/interceptor-java" && mvn -q -DskipTests package)
    fi

    if [[ ! -f "$INTERCEPTOR_JAR" ]]; then
        log_error "Unable to locate or build the interceptor JAR."
        return 1
    fi

    log_info "Starting interceptor from $INTERCEPTOR_JAR"
    nohup java \
        -Dspring.datasource.url="jdbc:mysql://localhost:3306/$MYSQL_DB" \
        -Dspring.datasource.username="$MYSQL_USER" \
        -Dspring.datasource.password="$MYSQL_PASSWORD" \
        -Dinterceptor.mqtt.broker-uri="tcp://localhost:1883" \
        -Dinterceptor.serial-port="${SERIAL_PORT:-/dev/ttyRS485}" \
        -jar "$INTERCEPTOR_JAR" > /tmp/traincontroller-interceptor.log 2>&1 &

    echo $! > "$INTERCEPTOR_PID_FILE"
    sleep 2

    if kill -0 "$(cat "$INTERCEPTOR_PID_FILE")" 2>/dev/null; then
        log_info "Interceptor started successfully."
        return 0
    fi

    log_error "Interceptor process exited unexpectedly. See /tmp/traincontroller-interceptor.log"
    return 1
}

show_status() {
    echo ""
    log_info "Native TrainController services"
    echo "  MySQL:      ${MYSQL_DB} on localhost:3306"
    echo "  Mosquitto:  localhost:1883"
    echo "  Interceptor: http://localhost:8080/actuator/health"
    echo ""
    echo "To stop everything: stop_traincontroller"
    echo "To start again: start_traincontroller"
    echo ""
}

start_traincontroller() {
    require_cmd mosquitto || return 1
    require_cmd mysql || return 1
    require_cmd java || return 1
    require_cmd python3 || return 1

    ensure_mysql_running
    ensure_database_schema
    ensure_servo_defaults
    ensure_mosquitto_running
    ensure_interceptor_running
    show_status
}

stop_traincontroller() {
    if [[ -f "$INTERCEPTOR_PID_FILE" ]]; then
        local pid
        pid="$(cat "$INTERCEPTOR_PID_FILE" 2>/dev/null || true)"
        if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
            log_info "Stopping interceptor (pid $pid)"
            kill "$pid" || true
        fi
        rm -f "$INTERCEPTOR_PID_FILE"
    fi

    if systemctl is-active --quiet mosquitto 2>/dev/null; then
        log_info "Stopping Mosquitto"
        sudo systemctl stop mosquitto || true
    fi

    if systemctl is-active --quiet mysql 2>/dev/null; then
        log_info "Stopping MySQL"
        sudo systemctl stop mysql || true
    fi

    if systemctl is-active --quiet mariadb 2>/dev/null; then
        log_info "Stopping MariaDB"
        sudo systemctl stop mariadb || true
    fi
}

if [[ "${TRAINCONTROLLER_AUTO_START:-0}" == "1" ]]; then
    start_traincontroller
fi

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    case "${1:-start}" in
        start)
            start_traincontroller
            ;;
        stop)
            stop_traincontroller
            ;;
        status)
            show_status
            ;;
        *)
            echo "Usage: $0 [start|stop|status]"
            exit 1
            ;;
    esac
fi
