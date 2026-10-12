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

### Ubuntu native install (desktop / dedicated train computer)

Install the required packages:

```bash
sudo apt update
sudo apt install -y mysql-server mysql-client mosquitto openjdk-21-jdk maven curl
```

Enable the services:

```bash
sudo systemctl enable --now mysql
sudo systemctl enable --now mosquitto
```

Create the database and app user:

```bash
sudo mysql -uroot -e "CREATE DATABASE IF NOT EXISTS train_controller;"
sudo mysql -uroot -e "CREATE USER IF NOT EXISTS 'train'@'localhost' IDENTIFIED BY 'train';"
sudo mysql -uroot -e "GRANT ALL PRIVILEGES ON train_controller.* TO 'train'@'localhost';"
sudo mysql -uroot -e "FLUSH PRIVILEGES;"
```

Apply the project schema and servo defaults:

```bash
cd ~/Arduino/TrainController
for f in migrations/V*.sql; do
  mysql -u train -ptrain train_controller < "$f"
done
```

Build the interceptor:

```bash
cd ~/Arduino/TrainController/interceptor-java
mvn -q -DskipTests package
```

### Native Startup (recommended for desktop / dedicated train computer)

Add this to either `~/.bashrc` or `~/.profile`:

```bash
source ~/Arduino/TrainController/ops/start-native.sh
start_traincontroller
```

This starts MySQL, Mosquitto, and the Java interceptor natively and preserves persisted servo state across restarts. The script checks whether `device_servo_config` already contains values before seeding default angles, and only falls back to the JSON config in `ops/servo-defaults.json` when the table is empty.

`start_traincontroller` is idempotent: if the services are already running, it will skip unnecessary restarts.

### Final desktop startup sequence

Run this after each reboot or login on the train computer:

```bash
sudo systemctl start mysql
sudo systemctl start mosquitto
source ~/Arduino/TrainController/ops/start-native.sh
start_traincontroller
```

If you prefer a one-liner for login startup:

```bash
source ~/Arduino/TrainController/ops/start-native.sh
start_traincontroller
```

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
