# Architecture

## Command Flow

1. JMRI emits turnout intent (desired state).
2. Interceptor validates and deduplicates by correlation id and command id.
3. Interceptor writes desired state to MySQL first.
4. Interceptor dispatches RS485 command to target Arduino node.
5. Node returns acknowledgement.
6. Interceptor waits settle delay and requests actual node state.
7. Interceptor compares desired vs actual and either verifies, retries, or marks failed.

## Reliability Model

- Delivery guarantee: at-least-once with idempotent actuation.
- Duplicate safety: command id and correlation id checks.
- Reconciliation: polling loop with capped retries/backoff.
- Auditability: persistent event history and state transition tracking.

## Primary Components

- Inbound adapter (JMRI event ingress).
- Command normalizer and idempotency gate.
- Persistence service (desired/actual state, queue, history).
- RS485 transport and protocol codec.
- Reconciliation worker.
- Metrics and health endpoints.

## Design Documents

- `docs/design/01-domain-lifecycle-and-operations.md`
- `docs/design/02-mqtt-topics-and-message-contract.md`
- `docs/design/03-sql-model-and-migrations-plan.md`
- `docs/design/04-adr-001-di-framework-spring-vs-guice.md`

## Operations Runbooks

- `docs/operations/hardware-soak-validation.md`
