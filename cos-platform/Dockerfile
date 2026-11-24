# Multi-stage build: one shared build stage compiles all three runnable modules,
# then one slim runtime stage per module copies out only what it needs.
# cos-bureau-mocks has no Spring Boot fat jar, so it uses `installDist` (script + lib/*.jar)
# instead of the `bootJar` used for the two Spring Boot services.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :cos-bureau-mocks:installDist :cos-decision-service:bootJar :cos-orchestration:bootJar \
    --no-daemon -x test

FROM eclipse-temurin:21-jre AS bureau-mocks
WORKDIR /app
COPY --from=build /workspace/cos-bureau-mocks/build/install/cos-bureau-mocks ./
EXPOSE 9561
ENTRYPOINT ["./bin/cos-bureau-mocks"]

FROM eclipse-temurin:21-jre AS decision-service
WORKDIR /app
COPY --from=build /workspace/cos-decision-service/build/libs/cos-decision-service.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre AS orchestration
WORKDIR /app
COPY --from=build /workspace/cos-orchestration/build/libs/cos-orchestration.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
