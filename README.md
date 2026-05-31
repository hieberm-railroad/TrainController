# TrainController

TrainController is a monorepo for model railroad control with JMRI as orchestration and a standalone Java interceptor responsible for reliable physical execution.

## Goals

- Keep JMRI as logic and routing layer.
- Persist desired and actual turnout state in MySQL.
- Deliver hardware commands over RS485 to Arduino nodes.
- Reconcile desired vs actual state with retries and error handling.
- Keep implementation extensible and observable.

## Monorepo Layout

- `interceptor-java/` Java service for ingest, persistence, dispatch, and reconciliation.
- `firmware-arduino/` PlatformIO firmware for turnout and signal nodes.
- `protocol/` Message contracts and examples.
- `migrations/` SQL migrations for MySQL schema.
- `ops/` Production startup and configuration.
- `tests/` Integration and soak-test harness placeholders.
- `docs/` Architecture and operational documentation.

## Quick Start

### Full Stack (MySQL + Mosquitto + Interceptor)

```bash
cd ops
./startup.sh
```

This script builds the interceptor, starts all services, and verifies health. See [ops/README.md](ops/README.md) for details.

### Individual Service Startup (Development)

**Bring up infrastructure:**
```bash
cd ops
docker-compose up -d mysql mosquitto
```

**Run interceptor locally (useful for debugging):**
```bash
cd interceptor-java
mvn spring-boot:run
```

**Flash firmware:**
```bash
cd firmware-arduino
pio run -e turnout_node -t upload
```

## JMRI Integration

Configure JMRI to publish turnout state change intents via MQTT:

- **Topic**: `trains/track/turnout/turnout1`
- **Payload**: `THROWN` (maps to OPEN) or `CLOSED`
- **QoS**: 0 or 1

Example test command:
```bash
mosquitto_pub -h localhost -t trains/track/turnout/turnout1 -m THROWN
```

## System Requirements

- **Java 21+** (for building interceptor)
- **Docker & docker-compose**
- **Arduino Nano** with RS485 breakout
- **RS485 USB adapter** with device path `/dev/ttyRS485` (configurable via udev rules)

## Documentation

- [Architecture](docs/architecture.md)
- [Design Documents](docs/design/)
- [Operations & Soak Tests](docs/operations/)
- [Hardware Soak Validation](docs/operations/hardware-soak-validation.md)
