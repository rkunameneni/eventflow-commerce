# syntax=docker/dockerfile:1.7
# Build context: repository root, e.g. docker build -f payment-service/Dockerfile .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Poms first: dependency resolution is cached until a pom changes.
COPY pom.xml ./
COPY common-events/pom.xml common-events/
COPY service-support/pom.xml service-support/
COPY order-service/pom.xml order-service/
COPY inventory-service/pom.xml inventory-service/
COPY payment-service/pom.xml payment-service/
COPY notification-service/pom.xml notification-service/
COPY api-gateway/pom.xml api-gateway/
COPY acceptance-tests/pom.xml acceptance-tests/
COPY common-events/src common-events/src
COPY service-support/src service-support/src
COPY payment-service/src payment-service/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl payment-service -am -DskipTests package \
 && cp payment-service/target/payment-service-*-exec.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S eventflow && adduser -S -G eventflow -u 10001 eventflow
WORKDIR /app
# Layers ordered from least to most frequently changing, so a code change only ships the last one.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER 10001
EXPOSE 8083
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8083/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
