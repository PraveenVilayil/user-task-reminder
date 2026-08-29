# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Stage 1: build the whole reactor and produce the web module fat jar.
# The pom files are copied first so a source-only change does not invalidate
# the cached dependency download.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml .
COPY user-task-reminder-model/pom.xml   user-task-reminder-model/pom.xml
COPY user-task-reminder-db/pom.xml      user-task-reminder-db/pom.xml
COPY user-task-reminder-service/pom.xml user-task-reminder-service/pom.xml
COPY user-task-reminder-web/pom.xml     user-task-reminder-web/pom.xml

RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY user-task-reminder-model   user-task-reminder-model
COPY user-task-reminder-db      user-task-reminder-db
COPY user-task-reminder-service user-task-reminder-service
COPY user-task-reminder-web     user-task-reminder-web

# Tests run in CI against H2; the image build only needs the artefact.
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

# ---------------------------------------------------------------------------
# Stage 2: runtime. JRE only, no build tooling, no source.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Unprivileged account; the application never needs to write to the image.
RUN addgroup -S -g 1001 app && adduser -S -u 1001 -G app app

WORKDIR /app

COPY --from=build --chown=app:app /build/user-task-reminder-web/target/user-task-reminder.jar app.jar

USER app

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# Actuator readiness probe. wget is provided by the busybox base.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
