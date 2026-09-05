# ============================================================================
# Frontend CoachRun — build Angular (Node) → service statique (nginx)
# ============================================================================

# --- Stage 1 : build -------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Build de production (optimisation, hachage, service worker) SANS le DSN Sentry de production :
# une stack locale n'a rien à écrire dans le projet d'erreurs des utilisateurs réels.
# Cf. front/src/environments/environment.docker.ts.
#
# PRERENDER_API_ORIGIN n'est volontairement pas fourni ici : au moment où cette image se
# construit, l'API de la pile Docker n'écoute pas encore. Le script de collecte le dit et
# poursuit avec les seules pages statiques — c'est exactement le repli pour lequel il est écrit.
# Une pile locale n'a de toute façon pas de liens partagés à faire prévisualiser.
RUN npm run build:docker

# --- Stage 2 : runtime (nginx) ---------------------------------------------
FROM nginx:1.27-alpine AS runtime
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/front/browser /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
