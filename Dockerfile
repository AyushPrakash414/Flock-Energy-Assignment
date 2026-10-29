# ==========================================
# Stage 1: Build the Application
# ==========================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Pre-fetch dependencies to leverage Docker layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build the production JAR
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# Stage 2: Minimal Lightweight Runtime
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user for security best practices
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy JAR from the builder stage
COPY --from=builder /build/target/urja-meter-ops-1.0.0.jar app.jar

# Expose standard REST API port
EXPOSE 8080

# Environment defaults
ENV SERVER_PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod

# Container-aware JVM memory limits
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
