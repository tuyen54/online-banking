# ═══════════════════════════════════════════════════════════════
# Stage 1: Build JAR using Maven and Eclipse Temurin 21
# ═══════════════════════════════════════════════════════════════
FROM maven:3.9-eclipse-temurin-21-jammy AS builder

WORKDIR /build

# Copy Maven POM and pre-fetch dependencies for layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build production artifact
COPY src ./src
RUN mvn clean package -DskipTests

# ═══════════════════════════════════════════════════════════════
# Stage 2: Lightweight Production Runtime Image
# ═══════════════════════════════════════════════════════════════
FROM eclipse-temurin:21-jre-jammy

# Install curl for container health check
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Security: Create non-root system user and group
RUN groupadd -g 10001 banking && \
    useradd -u 10001 -g banking -s /bin/sh -m banking

WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /build/target/*.jar /app/app.jar
RUN chown -R banking:banking /app

# Switch to unprivileged non-root user
USER banking:banking

# Expose Spring Boot HTTP port
EXPOSE 8080

# Container healthcheck using Spring Boot Actuator
HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Container-optimized JVM memory parameters
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "/app/app.jar"]

