# Keep both stages on Java 25; the Gradle toolchain must not fall back to Java 21.
FROM eclipse-temurin:25-jdk-jammy AS builder
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
COPY src/main/ src/main/
# bootJar does not run tests; Testcontainers validation runs separately on a Docker host.
RUN chmod +x gradlew && ./gradlew --no-daemon --max-workers=2 bootJar

FROM eclipse-temurin:25-jre-jammy AS runtime
RUN groupadd --gid 10001 smartphset \
    && useradd --uid 10001 --gid smartphset --no-create-home --shell /usr/sbin/nologin smartphset
WORKDIR /app
COPY --from=builder --chown=10001:10001 /workspace/build/libs/*.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=render
# Leaves room for metaspace, code cache, direct buffers, thread stacks and native JVM memory.
# These are starting limits, not evidence of a measured 512 MiB working set.
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx224m -XX:+UseSerialGC -XX:MaxDirectMemorySize=32m -XX:ReservedCodeCacheSize=48m -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
