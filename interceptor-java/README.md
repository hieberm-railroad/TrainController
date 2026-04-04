# interceptor-java

Standalone Java service that receives JMRI intent events, persists desired state, sends commands over RS485, and reconciles actual turnout state.

## Build

```bash
mvn -q test
mvn -q package
```

## Run

```bash
mvn spring-boot:run
```
