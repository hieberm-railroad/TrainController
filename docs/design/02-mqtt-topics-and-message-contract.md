# MQTT Topics And Message Contract

## Purpose

Define topic taxonomy, QoS, retain behavior, and payload envelope for intent, dispatch, acknowledgment, and telemetry paths.

## Topic Taxonomy

- tc/v1/intent/{deviceType}/{deviceId}
- tc/v1/cmd/{nodeId}
- tc/v1/ack/{nodeId}
- tc/v1/state/{deviceType}/{deviceId}
- tc/v1/event/{category}

Examples:

- tc/v1/intent/turnout/turnout-12
- tc/v1/cmd/node-01
- tc/v1/ack/node-01
- tc/v1/state/signal/signal-A1
- tc/v1/event/reconciliation

## Envelope (All Payloads)

All payloads include:

- schemaVersion (string, for example v1)
- messageType (INTENT, COMMAND, ACK, STATE, EVENT)
- commandId (string)
- correlationId (string)
- deviceType (TURNOUT, SIGNAL)
- deviceId (string)
- nodeId (string)
- emittedAt (ISO-8601 UTC)

## Message Types

### INTENT

Source: upstream orchestration (for example JMRI adapter)
Topic: tc/v1/intent/{deviceType}/{deviceId}

Fields:

- desiredState (string)
- requestedBy (string, optional)
- ttlMs (number, optional)
- priority (number, optional)

### COMMAND

Source: interceptor dispatcher
Topic: tc/v1/cmd/{nodeId}

Fields:

- operationType (TURNOUT_SET, SIGNAL_SET)
- desiredState (string)
- attemptNumber (integer)
- checksum (string, optional if transport already protected)

### ACK

Source: field node
Topic: tc/v1/ack/{nodeId}

Fields:

- ackStatus (ACCEPTED, REJECTED, DUPLICATE, STALE)
- reasonCode (string, optional)
- observedState (string, optional)

### STATE

Source: field node or poll worker
Topic: tc/v1/state/{deviceType}/{deviceId}

Fields:

- actualState (string)
- source (POLL, EVENT)
- quality (GOOD, DEGRADED, UNKNOWN)

### EVENT

Source: interceptor
Topic: tc/v1/event/{category}

Fields:

- category (transport, reconciliation, validation, system)
- severity (INFO, WARN, ERROR)
- detail (string)

## Delivery Semantics

- intent: QoS 1, retain false
- cmd: QoS 1, retain false
- ack: QoS 1, retain false
- state: QoS 1, retain true (last-known state useful)
- event: QoS 0 or 1 based on category, retain false

## Idempotency Rules

- commandId is required and globally unique for active command horizon.
- Receiver must ignore duplicate commandId for already-applied command.
- ACK with unknown commandId is recorded as orphaned transport event.

## Ordering And Expiration

- Ordering is best-effort within topic partition.
- Consumers apply emittedAt and ttlMs guards.
- Expired commands move to FAILED with reason EXPIRED_BEFORE_EXECUTION.

## Validation Baseline

- Reject if required envelope fields are missing.
- Reject if desiredState is not allowed for deviceType.
- Reject if schemaVersion is unsupported.
