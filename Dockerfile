# Multi-stage build for optimization
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY src/main/java/com/daya/project/sentiment_ledger/dto .
RUN ./gradlew clean build -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy jar from builder
COPY --from=builder /build/build/libs/*.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run as non-root
RUN addgroup -g 1000 appuser && adduser -u 1000 -G appuser appuser
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]