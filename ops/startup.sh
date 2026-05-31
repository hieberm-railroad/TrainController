#!/usr/bin/env bash
set -euo pipefail

# TrainController production startup script

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
COMPOSE_CMD=""

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# Check prerequisites
check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker not found. Install Docker and try again."
        exit 1
    fi
    if ! docker ps > /dev/null 2>&1; then
        log_error "Docker daemon not running or missing permissions."
        exit 1
    fi
}

check_docker_compose() {
    if command -v docker-compose &> /dev/null; then
        COMPOSE_CMD="docker-compose"
    elif docker compose version &> /dev/null 2>&1; then
        COMPOSE_CMD="docker compose"
    else
        log_error "docker-compose or 'docker compose' not found."
        exit 1
    fi
}

check_java() {
    if ! command -v java &> /dev/null; then
        log_error "Java not found. Install Java 21+ to build interceptor."
        exit 1
    fi
    JAVA_VERSION=$(java -version 2>&1 | grep -oP '(?<=version ")[^"]*' | cut -d'.' -f1)
    if [[ "$JAVA_VERSION" -lt 21 ]]; then
        log_error "Java 21+ required. Current: Java $JAVA_VERSION"
        exit 1
    fi
    log_info "Java version check passed: $JAVA_VERSION"
}

setup_udev_rules() {
    log_info "Checking USB device mappings..."
    
    if ! lsusb &> /dev/null; then
        log_warn "lsusb not found; skipping automatic udev setup. Install usbutils: sudo apt install usbutils"
        return 1
    fi

    UDEV_RULES_FILE="/etc/udev/rules.d/50-traincontroller.rules"
    if [[ -f "$UDEV_RULES_FILE" ]]; then
        log_info "udev rules already present: $UDEV_RULES_FILE"
        return 0
    fi

    log_warn "USB device rules not found."
    log_info "To set up consistent USB naming, run:"
    echo ""
    echo "  lsusb"
    echo "  udevadm info --name=/dev/ttyUSB0 --export | grep -E 'ID_SERIAL|ID_PATH|PRODUCT'"
    echo ""
    echo "Then create /etc/udev/rules.d/50-traincontroller.rules with (as root):"
    echo ""
    echo "  # RS485 adapter"
    echo "  SUBSYSTEMS==\"usb\", ATTRS{idVendor}==\"1a86\", ATTRS{idProduct}==\"7523\", SYMLINK+=\"ttyRS485\""
    echo ""
    echo "  # Arduino Nano"
    echo "  SUBSYSTEMS==\"usb\", ATTRS{idVendor}==\"2341\", ATTRS{idProduct}==\"6001\", SYMLINK+=\"ttyArduino\""
    echo ""
    echo "Then reload: sudo udevadm control --reload-rules && sudo udevadm trigger"
    echo ""
    
    read -p "Continue without setting up udev? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "Aborting. Set up udev rules first."
        exit 1
    fi
}

build_interceptor() {
    log_info "Building interceptor Docker image..."
    cd "$REPO_ROOT"
    docker build -f ops/Dockerfile -t train-interceptor:latest . 2>&1 | tail -20
    if [[ ${PIPESTATUS[0]} -ne 0 ]]; then
        log_error "Docker build failed."
        exit 1
    fi
    log_info "Interceptor image built."
}

cleanup_stale_containers() {
    log_info "Cleaning up stale containers..."
    $COMPOSE_CMD down --remove-orphans 2>/dev/null || true
    if docker ps -a --filter "name=ops-" | grep -q ops-; then
        log_info "Removing orphaned containers..."
        docker ps -a --filter "name=ops-" --format "{{.ID}}" | xargs -r docker rm -f 2>/dev/null || true
    fi
}

check_ports() {
    log_info "Checking required ports..."
    local required_ports=(1883 3306 8080)
    for port in "${required_ports[@]}"; do
        if lsof -Pi :$port -sTCP:LISTEN -t &> /dev/null; then
            log_warn "Port $port is already in use by a process outside Docker."
            case $port in
                1883)
                    log_info "Mosquitto port conflict. Kill system mosquitto:"
                    echo "  sudo systemctl stop mosquitto"
                    echo "  sudo systemctl disable mosquitto"
                    ;;
                3306)
                    log_info "MySQL port conflict. Check: sudo lsof -i :3306"
                    ;;
                8080)
                    log_info "Interceptor port conflict. Check: sudo lsof -i :8080"
                    ;;
            esac
            exit 1
        fi
    done
    log_info "Port check passed."
}

start_services() {
    log_info "Starting services..."
    cd "$SCRIPT_DIR"
    cleanup_stale_containers
    $COMPOSE_CMD up -d
    log_info "Services started. Waiting for health checks..."
}

wait_health() {
    local max_retries=60
    local retry=0
    
    while [[ $retry -lt $max_retries ]]; do
        if $COMPOSE_CMD exec -T mysql mysqladmin ping -h localhost -u train -ptrain &> /dev/null; then
            log_info "MySQL is healthy."
            break
        fi
        retry=$((retry + 1))
        if [[ $((retry % 10)) -eq 0 ]]; then
            log_info "Waiting for MySQL... ($retry/$max_retries)"
        fi
        sleep 1
    done

    if [[ $retry -eq $max_retries ]]; then
        log_error "MySQL failed to become healthy."
        $COMPOSE_CMD logs mysql
        exit 1
    fi

    retry=0
    while [[ $retry -lt 30 ]]; do
        if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
            log_info "Interceptor is healthy."
            break
        fi
        retry=$((retry + 1))
        if [[ $((retry % 5)) -eq 0 ]]; then
            log_info "Waiting for interceptor... ($retry/30)"
        fi
        sleep 1
    done

    if [[ $retry -eq 30 ]]; then
        log_warn "Interceptor health check did not pass within 30s. Checking logs..."
        $COMPOSE_CMD logs interceptor | tail -30
    fi
}

print_status() {
    echo ""
    log_info "Services running."
    echo ""
    echo "  MySQL:       localhost:3306 (user: train, pass: train)"
    echo "  Mosquitto:   localhost:1883"
    echo "  Interceptor: http://localhost:8080"
    echo "  Metrics:     http://localhost:8080/actuator/prometheus"
    echo ""
    log_info "JMRI Integration"
    echo ""
    echo "  Configure JMRI to publish turnout intents to:"
    echo "    Topic:   trains/track/turnout/turnout1"
    echo "    Payload: THROWN (opens) or CLOSED"
    echo ""
    echo "  Example test:"
    echo "    mosquitto_pub -h localhost -t trains/track/turnout/turnout1 -m THROWN"
    echo ""
    log_info "To stop all services:"
    echo "    cd $SCRIPT_DIR && $COMPOSE_CMD down"
    echo ""
}

main() {
    log_info "TrainController Production Startup"
    log_info "Checking prerequisites..."
    check_docker
    check_docker_compose
    check_java
    check_ports
    setup_udev_rules || true
    build_interceptor
    start_services
    wait_health
    print_status
}

main "$@"
