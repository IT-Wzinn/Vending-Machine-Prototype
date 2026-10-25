# Multi-stage Dockerfile for Vending Machine Backend (Java 21 / Spring Boot 3.3.5)
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Copy pom.xml and pre-fetch dependencies for Docker layer caching
COPY pom.xml .
RUN mvn dependency:resolve -B

# Copy source code and build production executable JAR
COPY src src
RUN mvn clean package -DskipTests

# Minimal runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as dedicated unprivileged user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /workspace/target/backend-0.0.1-SNAPSHOT.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
