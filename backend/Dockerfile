# ==============================================================================
# NOVA PETZLIFE — Production Multi-Stage Backend Dockerfile
# Base: Eclipse Temurin Java 21 (Alpine Linux) + Maven 3.9
# ==============================================================================

# Stage 1: Build the Spring Boot application
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Pre-cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and compile production jar
COPY src src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create unprivileged user for security compliance
RUN addgroup -S novapetzlife && adduser -S novapetzlife -G novapetzlife

# Copy built artifact from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Set ownership
RUN chown -R novapetzlife:novapetzlife /app

USER novapetzlife

# Expose Spring Boot application port
EXPOSE 8080

# Production JVM options (container-aware, memory-safe for 512MB RAM)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
