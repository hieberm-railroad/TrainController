# ops

Local runtime stack for development and integration testing.

## Start dependencies

```bash
docker compose up -d
```

Services:

- MySQL on `localhost:3306`
- Mosquitto MQTT broker on `localhost:1883`

## Optional Observability

Start Prometheus with alert rules:

```bash
docker compose --profile observability up -d prometheus
```

Prometheus UI: `http://localhost:9090`

Configured files:

- `ops/prometheus/prometheus.yml`
- `ops/prometheus/interceptor-alerts.yml`

Default scrape target assumes interceptor is running on host port `8080` and exposing
`/actuator/prometheus`.
