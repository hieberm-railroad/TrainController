# Soak Snapshot - 2026-04-17

## Scope
- Load method: 300 alternating turnout commands at 1 Hz, injected into MySQL as `DISPATCH_READY` (store-first path).
- Interceptor run mode: `interceptor-java` running via Spring Boot with serial transport enabled (`/dev/ttyUSB0`).
- Dependency stack: MySQL, Mosquitto, and Prometheus from `ops/docker-compose.yml`.

## Snapshot Time
- Captured on 2026-04-17 (local run session)

## Key Metrics From /actuator/prometheus
- `interceptor_transport_send_total{outcome="transport_error"}`: `429.0`
- `interceptor_transport_retry_scheduled_total`: `375.0`
- `interceptor_transport_failed_total`: `54.0`

## Runbook Query Snapshots (Prometheus API)
- Transport error ratio (5m): `1`
- Readback failure ratio (5m): `NO_SERIES`
- Verification retry rate (5m): `NO_SERIES`
- P95 lifecycle latency (10m): `NO_SERIES`

## DB State Snapshot
- `DISPATCH_READY`: `25`
- `SENT`: `183`
- `FAILED`: `246`
- `VERIFIED`: `140`
- `command_attempt` rows: `1683`

## Notes
- Prometheus scrape target is configured to `172.17.0.1:8080` in `ops/prometheus/prometheus.yml` and was up during capture.
- The run primarily exercised transport/retry/fail behavior; readback/verification metrics were not emitted as active time series in this capture window.
- Interceptor logs show repeated serial timeouts while sending framed commands, consistent with high transport-error ratio.
