# ops

Production startup and infrastructure configuration.

## Quick Start

### Prerequisites

- Docker and docker-compose
- Java 21+ (for building interceptor image)
- USB devices consistently named (udev rules recommended)

### Automated Setup

```bash
./startup.sh
```

This script:
1. Validates prerequisites
2. Builds interceptor Docker image
3. Starts MySQL, Mosquitto, and interceptor
4. Applies SQL migrations automatically
5. Verifies health checks

Output includes JMRI integration instructions and endpoint details.

### Manual Startup

```bash
docker-compose up -d
```

Services:
- MySQL on `localhost:3306` (user: `train`, pass: `train`)
- Mosquitto MQTT broker on `localhost:1883`
- Interceptor on `localhost:8080`

### Shutdown

```bash
./shutdown.sh
```

Or: `docker-compose down`

## USB Device Configuration

For consistent `/dev/ttyRS485` and `/dev/ttyArduino` naming, create `/etc/udev/rules.d/50-traincontroller.rules`:

```udev
# RS485 adapter (adjust vendor/product IDs for your hardware)
SUBSYSTEMS=="usb", ATTRS{idVendor}=="1a86", ATTRS{idProduct}=="7523", SYMLINK+="ttyRS485"

# Arduino Nano (adjust vendor/product IDs for your hardware)
SUBSYSTEMS=="usb", ATTRS{idVendor}=="2341", ATTRS{idProduct}=="6001", SYMLINK+="ttyArduino"
```

Find your device IDs:
```bash
lsusb
udevadm info --name=/dev/ttyUSB0 --export | grep -E 'ID_SERIAL|PRODUCT'
```

Apply rules:
```bash
sudo udevadm control --reload-rules
sudo udevadm trigger
```

## Observability

Enable Prometheus:

```bash
docker-compose --profile observability up -d
```

UI: `http://localhost:9090`

Configured with:
- `ops/prometheus/prometheus.yml`
- `ops/prometheus/interceptor-alerts.yml`

## Environment Overrides

Docker Compose reads environment variables. Customize via `.env` or inline:

```bash
SERIAL_PORT=/dev/ttyArduino MQTT_CLIENT_ID=interceptor-prod-1 docker-compose up -d interceptor
```

Supported variables (see `ops/application.yml`):
- `SERIAL_PORT` (default: `/dev/ttyRS485`)
- `MQTT_BROKER_URI` (default: `tcp://mosquitto:1883`)
- `DB_USER`, `DB_PASSWORD`
- `SETTLE_DELAY_MS`, `MAX_RETRIES`, `RETRY_BACKOFF_MS`
- `MQTT_INBOUND_TOPIC` (default: `trains/track/turnout/+`)
