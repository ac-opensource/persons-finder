# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e

FROM eclipse-temurin:17-jdk-jammy@sha256:723151f3fc88ca2060153ee08ab8dbbea7983d6ed6f2622fe440acf178737c94 AS build

WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon --console=plain

FROM eclipse-temurin:17-jre-jammy@sha256:475d8e96b4b2bfe08999e5e854755c773af1581acdf959a4545d88f0696a2339 AS runtime

RUN groupadd --system persons-finder \
    && useradd --system --gid persons-finder --home-dir /app persons-finder

WORKDIR /app

COPY --from=build --chown=persons-finder:persons-finder \
    /workspace/build/libs/PersonsFinder-0.0.1-SNAPSHOT.jar \
    /app/persons-finder.jar

USER persons-finder

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/persons-finder.jar"]
