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

# Fuseau du conteneur : sans lui la JVM tourne en UTC et toutes les dates civiles
# (séance du jour, check-in, rappels) sont décalées d'une à deux heures.
ENV TZ=Europe/Paris

# curl pour le healthcheck docker-compose / Railway ; utilisateur non-root
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system coachrun && useradd --system --gid coachrun coachrun

COPY --from=build /app/target/coachrun-back.jar app.jar
RUN chown -R coachrun:coachrun /app
USER coachrun

# Mémoire : Railway facture la mémoire réellement occupée, et le conteneur y voit 32 Go.
# Avec un pourcentage de la RAM (ancien réglage MaxRAMPercentage=75), la JVM s'autorisait
# 24 Go de tas, le laissait grossir et ne le rendait jamais : ~0,9 Go en continu pour
# quelques coachs. On fixe donc des plafonds absolus. Le ramasse-miettes série est le plus
# économe pour un petit tas, et MALLOC_ARENA_MAX limite la mémoire native de glibc.
# Réglable sans recompiler : poser JAVA_OPTS dans les variables Railway (remplace la valeur).
ENV MALLOC_ARENA_MAX=2 \
    JAVA_OPTS="-Xms128m -Xmx512m -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=64m -Xss512k -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
