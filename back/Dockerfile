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

# --- Commit déployé ----------------------------------------------------------
# Le contexte de build ne contient pas `.git` (et on ne veut pas l'y copier : le dépôt entier
# pour sept caractères). Le commit est donc passé en argument, et relu au démarrage par
# BuildInfoContributor pour alimenter /api/actuator/info — la seule façon de savoir QUEL code
# tourne quand une erreur remonte (OPS-09).
#
#   docker build --build-arg APP_COMMIT=$(git rev-parse HEAD) ...
#
# Sur Railway, `RAILWAY_GIT_COMMIT_SHA` est posé à l'exécution et prend le relais tout seul.

# --- Stage 2 : runtime -------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

ARG APP_COMMIT=""
ENV APP_COMMIT=${APP_COMMIT}

# Fuseau du conteneur : sans lui la JVM tourne en UTC et toutes les dates civiles
# (séance du jour, check-in, rappels) sont décalées d'une à deux heures.
ENV TZ=Europe/Paris

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
