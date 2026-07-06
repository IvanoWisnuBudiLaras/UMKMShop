# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jdk-jammy AS gradle-build

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY app/build.gradle.kts app/build.gradle.kts
COPY backend/build.gradle.kts backend/build.gradle.kts

RUN chmod +x ./gradlew && \
    ./gradlew --no-daemon --version

COPY app app
COPY backend backend

RUN ./gradlew --no-daemon :backend:installDist

FROM eclipse-temurin:17-jre-alpine AS backend

WORKDIR /opt/umkmshop
RUN adduser -S -u 10001 -h /opt/umkmshop -s /sbin/nologin umkmshop

COPY --from=gradle-build /workspace/backend/build/install/backend ./backend

ENV JAVA_OPTS="-Xms128m -Xmx384m -XX:+ExitOnOutOfMemoryError" \
    UMKMSHOP_BACKEND_PORT="8090" \
    UMKMSHOP_WORKER_ENABLED="false" \
    UMKMSHOP_BACKEND_QUEUE_NAME="notifications" \
    UMKMSHOP_BACKEND_MAX_ATTEMPTS="5" \
    UMKMSHOP_BACKEND_VISIBILITY_TIMEOUT_SECONDS="30" \
    UMKMSHOP_BACKEND_BATCH_SIZE="10" \
    UMKMSHOP_BACKEND_MAX_CONCURRENCY="8" \
    UMKMSHOP_BACKEND_EMPTY_POLL_DELAY_MS="2000" \
    UMKMSHOP_BACKEND_DB_POOL_MAX_SIZE="8" \
    UMKMSHOP_OAUTH_ISSUER="http://localhost:8090"

EXPOSE 8090
USER umkmshop
ENTRYPOINT ["/opt/umkmshop/backend/bin/backend"]
