FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn -B -q package -DskipTests
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 bank
WORKDIR /app
ARG MODULE
COPY --from=build /build/${MODULE}/target/${MODULE}-1.0.0.jar app.jar
COPY config-repo /app/config-repo
COPY data /app/data
RUN mkdir -p /app/runtime && chown bank:bank /app/runtime
USER 10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java","-jar","app.jar"]
