# Multi-stage Dockerfile for Vending Machine Backend (Java 21 / Spring Boot 3.3.5)
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy maven wrapper and project definition
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw

# Cache dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code and build executable jar
COPY src src
RUN ./mvnw clean package -DskipTests

# Minimal runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as dedicated unprivileged user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /workspace/target/backend-0.0.1-SNAPSHOT.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
