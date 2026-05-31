FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY interceptor-java /build
RUN mvn -q clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /build/target/interceptor-java-0.1.0-SNAPSHOT.jar app.jar
COPY ops/application.yml /app/application.yml
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.config.location=file:/app/application.yml"]
