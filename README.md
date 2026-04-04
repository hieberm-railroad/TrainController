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
- `ops/` Local runtime stack (MySQL, MQTT broker).
- `tests/` Integration and soak-test harness placeholders.
- `docs/` Architecture and operational documentation.

## Start Order

1. Bring up local dependencies from `ops/docker-compose.yml`.
2. Apply SQL migrations in `migrations/`.
3. Run Java interceptor from `interceptor-java/`.
4. Flash firmware from `firmware-arduino/`.
