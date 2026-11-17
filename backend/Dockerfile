# ==============================================================================
# NOVA PETZLIFE — Production Multi-Stage Backend Dockerfile
# Base: Eclipse Temurin Java 21 (Alpine Linux)
# ==============================================================================

# Stage 1: Build the Spring Boot application
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Maven descriptor and download dependencies
COPY pom.xml .
COPY .mvn .mvn/
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B || true

# Copy source code and build jar
COPY src src
RUN ./mvnw clean package -DskipTests -B

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

# Production JVM options (optimized memory, container-aware)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=prod"]
