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

## Run

```bash
mvn spring-boot:run
```
