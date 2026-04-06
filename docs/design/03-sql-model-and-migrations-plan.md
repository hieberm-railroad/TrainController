# SQL Model And Migrations Plan

## Purpose

Define a normalized SQL model for auditability, idempotency, and reconciliation.

## Table Set (Initial)

1. device
2. device_state
3. intent
4. tc_command
5. command_attempt
6. command_event

## Table Definitions (Conceptual)

### device

- id (PK)
- device_type (TURNOUT, SIGNAL)
- node_id
- external_ref (optional)
- enabled (bool)
- created_at
- updated_at

Indexes:

- idx_device_type_id (device_type, id)
- idx_device_node_id (node_id)

### device_state

- device_id (PK, FK -> device.id)
- desired_state
- actual_state
- last_command_id (FK -> tc_command.command_id)
- state_quality (GOOD, DEGRADED, UNKNOWN)
- last_verified_at
- updated_at

Indexes:

- idx_device_state_updated_at (updated_at)

### intent

- intent_id (PK)
- correlation_id
- source_system
- device_id (FK -> device.id)
- operation_type
- desired_state
- requested_at
- accepted_at
- status (RECEIVED, REJECTED, PROCESSED, CANCELLED)
- reject_reason (nullable)

Indexes:

- uq_intent_correlation_device (correlation_id, device_id)
- idx_intent_status_requested_at (status, requested_at)

### tc_command

- command_id (PK)
- intent_id (FK -> intent.intent_id)
- correlation_id
- device_id (FK -> device.id)
- node_id
- operation_type
- desired_state
- command_status
- retry_count
- max_retries
- settle_delay_ms
- next_attempt_at (nullable)
- failure_reason (nullable)
- created_at
- updated_at

Indexes:

- idx_command_status_next_attempt (command_status, next_attempt_at)
- idx_command_device_created (device_id, created_at)
- idx_command_correlation (correlation_id)

### command_attempt

- id (PK bigint auto increment)
- command_id (FK -> tc_command.command_id)
- attempt_no
- sent_at
- acked_at (nullable)
- ack_status (nullable)
- transport_error (nullable)
- verify_started_at (nullable)
- verify_completed_at (nullable)
- verified (nullable bool)

Constraints:

- uq_attempt_command_no (command_id, attempt_no)

Indexes:

- idx_attempt_command_sent (command_id, sent_at)

### command_event

- id (PK bigint auto increment)
- command_id (FK -> tc_command.command_id)
- intent_id (FK -> intent.intent_id)
- event_type
- event_status
- detail_json (json)
- created_at

Indexes:

- idx_event_command_created (command_id, created_at)
- idx_event_intent_created (intent_id, created_at)

## Why This Model

- tc_command is the lifecycle aggregate root.
- command_attempt captures transport/reconciliation loop history.
- command_event provides immutable audit trail.
- device_state keeps a fast read model for current operations.

## Migration Plan

- V1: legacy bootstrap tables (`turnout_state`, `command_history`).
- V2: normalized command model (`device`, `device_state`, `intent`, `tc_command`, `command_attempt`, `command_event`) with baseline constraints and indexes.
- V3: optional partitioning/archival policy for command_event and command_attempt.

## Compatibility With Existing Schema

Current schema has turnout_state and command_history.
Transition approach:

1. Keep existing tables readable during bootstrap.
2. Introduce new tables with backfill for active turnout rows.
3. Deprecate turnout_state and command_history once service fully reads from new model.
