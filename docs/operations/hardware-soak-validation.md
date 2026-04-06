# Hardware Soak Validation

## Goal

Validate end-to-end reliability for command transport, ACK handling, readback verification,
and retry behavior under sustained real-node operation.

## Preconditions

- Java interceptor running locally with serial access to turnout node.
- Arduino turnout node connected and responding to TURNOUT + QSTATE frames.
- MySQL and Mosquitto running from `ops/docker-compose.yml`.
- Metrics endpoint available at `http://localhost:8080/actuator/prometheus`.
- Optional: Prometheus running from `ops/prometheus/prometheus.yml`.

## Run Plan

1. Start dependencies:

```bash
docker compose -f ops/docker-compose.yml up -d
```

2. Start interceptor service and confirm metrics endpoint:

```bash
cd interceptor-java
mvn spring-boot:run
curl -sf http://localhost:8080/actuator/prometheus | head
```

3. Drive command load for at least 30 minutes.

Use your intent publisher path (JMRI or MQTT bridge) to continuously alternate turnout states
for a single device every 1-2 seconds.

4. Record key metrics every 5 minutes:

- `interceptor_transport_send_total`
- `interceptor_readback_total`
- `interceptor_verification_total`
- `interceptor_command_lifecycle_duration_seconds_bucket`

## Pass/Fail Thresholds

- Transport error ratio <= 10% over 5m windows.
- Readback failure ratio (`io_error` + `invalid_frame`) <= 15% over 5m windows.
- Verification retries stay below sustained spike threshold:
  `rate(interceptor_verification_total{outcome="retry_scheduled"}[5m]) <= 0.5`
- P95 lifecycle latency <= 5 seconds.
- No sustained growth in `FAILED` terminal outcomes.

## Prometheus Queries

Transport error ratio:

```promql
sum(rate(interceptor_transport_send_total{outcome="transport_error"}[5m]))
/
clamp_min(sum(rate(interceptor_transport_send_total[5m])), 1)
```

Readback failure ratio:

```promql
sum(rate(interceptor_readback_total{outcome=~"io_error|invalid_frame"}[5m]))
/
clamp_min(sum(rate(interceptor_readback_total[5m])), 1)
```

Verification retry rate:

```promql
sum(rate(interceptor_verification_total{outcome="retry_scheduled"}[5m]))
```

P95 lifecycle latency:

```promql
histogram_quantile(
  0.95,
  sum(rate(interceptor_command_lifecycle_duration_seconds_bucket[10m])) by (le)
)
```

## Exit Criteria

- All thresholds satisfied for the final 15-minute window.
- No unexpected command state transitions in DB audits.
- No serial framing anomalies beyond configured tolerances.
