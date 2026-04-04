# ADR-001: Dependency Injection Framework - Spring Vs Guice

- Status: Proposed
- Date: 2026-04-04

## Context

The interceptor currently uses Spring wiring. The team wants a design-first phase and is considering Guice for DI to keep runtime lightweight and wiring explicit.

## Decision Drivers

- Keep domain and transport contracts framework-agnostic.
- Minimize hidden runtime behavior.
- Preserve operational reliability (config, health, metrics, lifecycle).
- Keep onboarding complexity reasonable.

## Options

### Option A: Keep Spring

Pros:

- Mature ecosystem for config, validation, lifecycle, metrics, and health.
- Existing code already wired.
- Faster path to first production behavior.

Cons:

- Higher framework surface area.
- More implicit behavior via autoconfiguration.

### Option B: Switch To Guice

Pros:

- Explicit module wiring and lower abstraction overhead.
- Small DI footprint.
- Clear separation of interfaces and bindings.

Cons:

- Must build or choose replacements for config loading, lifecycle hooks, health endpoints, metrics exposure.
- More bootstrap code before feature work.

## Proposed Decision

Proceed with Guice only if done as an infrastructure swap after contracts are frozen.

Guardrails:

1. First finalize lifecycle, MQTT, and SQL specs.
2. Define service interfaces independent of Spring/Guice.
3. Implement Guice modules for wiring only.
4. Add explicit infrastructure components:
   - Config provider
   - Application lifecycle manager
   - Metrics facade
   - Health check endpoint
5. Migrate in small slices and keep behavior parity tests.

## Migration Outline

1. Create framework-neutral interfaces in interceptor-java (transport, repositories, reconciliation engine).
2. Introduce Guice modules that bind existing implementations.
3. Move app bootstrap to Guice injector + explicit startup sequence.
4. Replace Spring-specific configuration and lifecycle features.
5. Remove Spring dependencies once parity is validated.

## Consequences

Positive:

- Cleaner architecture boundaries and explicit dependencies.
- Easier reasoning about object graph.

Negative:

- Near-term engineering overhead before delivering new features.
- Operational concerns need explicit implementation instead of framework defaults.

## Revisit Criteria

Revisit this ADR if:

- Team velocity drops due to infrastructure work.
- Operational observability/regression risk increases.
- Guice migration cost exceeds one milestone.
