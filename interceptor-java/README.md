# interceptor-java

Standalone Java service that receives JMRI intent events, persists desired state, sends commands over RS485, and reconciles actual turnout state.

## Java Toolchain

This module targets Java 21 (see `pom.xml` `java.version=21`).

If you run Maven with Java 17, build/test will fail with:

`release version 21 not supported`

Check your active Java:

```bash
java -version
mvn -v
```

Run Maven using a Java 21 `JAVA_HOME` (or set up Maven toolchains) before running tests.

## Build

```bash
mvn -q test
mvn -q package
```

## Telemetry Metrics

The interceptor emits Micrometer metrics for transport and verification flow:

- interceptor.transport.send.total (tag: outcome=ack|no_ack|transport_error)
- interceptor.transport.send.duration (tag: outcome=ack|no_ack|transport_error)
- interceptor.transport.retry_scheduled.total
- interceptor.transport.failed.total
- interceptor.readback.total (tag: outcome=ok|invalid_frame|io_error)
- interceptor.readback.duration (tag: outcome=ok|invalid_frame|io_error)
- interceptor.verification.total (tag: outcome=verified|retry_scheduled|failed|ignored_unknown|ignored_non_acked)
- interceptor.command.lifecycle.duration (tag: outcome=verified|failed)

## Run

```bash
mvn spring-boot:run
```
