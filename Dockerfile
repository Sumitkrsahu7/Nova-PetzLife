# ==============================================================================
# NOVA PETZLIFE — Root Production Dockerfile for Render / Cloud Deployments
# Context: Repository Root
# Target: Java 21 Spring Boot Backend
# ==============================================================================

# Stage 1: Build the Spring Boot application
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Pre-cache Maven dependencies
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy backend source code and compile production jar
COPY backend/src src
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

# Expose port (Render automatically routes traffic via $PORT)
EXPOSE 8080

# Production JVM options (container-aware, memory-safe for Render Free/Starter 512MB RAM, prefer IPv4 for cloud DBs)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -Djava.net.preferIPv4Stack=true"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
