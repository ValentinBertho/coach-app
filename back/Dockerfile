# ============================================================================
# Backend CoachRun — build multi-stage : Maven (build) → JRE 21 slim (runtime)
# ============================================================================

# --- Stage 1 : build ---------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache des dépendances (couche séparée du code)
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# --- Stage 2 : runtime -------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# curl pour le healthcheck docker-compose / Railway ; utilisateur non-root
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system coachrun && useradd --system --gid coachrun coachrun

COPY --from=build /app/target/coachrun-back.jar app.jar
RUN chown -R coachrun:coachrun /app
USER coachrun

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
