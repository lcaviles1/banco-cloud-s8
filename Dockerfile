# Una imagen independiente por modulo; la compilacion se comparte por cache.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp verify
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl && addgroup -S bank && adduser -S -G bank bank && mkdir /data && chown bank:bank /data
WORKDIR /app
COPY config-repo /config-repo
ARG MODULE
COPY --from=build /src/${MODULE}/target/${MODULE}-1.0.0.jar app.jar
USER bank
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java","-jar","/app/app.jar"]
