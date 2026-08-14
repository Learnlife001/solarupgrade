# Build stage -------------------------------------------------------------
# Gradle and the JDK are only needed to produce the jar, so they stay out of
# the final image.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy the wrapper first so dependency resolution caches independently of
# source changes.
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
# Tests run in CI against a real database; repeating them here would only slow
# the deploy down and cannot catch anything CI did not.
RUN ./gradlew --no-daemon clean bootJar -x test

# Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as a non-root user rather than root.
RUN useradd --system --create-home --uid 10001 spring
USER spring

COPY --from=build --chown=spring:spring /workspace/build/libs/*.jar app.jar

# Render injects PORT and expects the process to bind it.
ENV PORT=8080
EXPOSE 8080

# Container-aware heap sizing: without this the JVM sizes against the host's
# memory, not the container limit, and gets OOM-killed on small instances.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=$PORT -jar app.jar"]
