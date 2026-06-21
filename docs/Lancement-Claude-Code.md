# Lancement du développement avec Claude Code

> Ce fichier contient **(1)** la commande/prompt à copier-coller dans Claude Code pour démarrer le projet,
> et **(2)** des annexes concrètes (Docker, Vercel, Railway, variables d'environnement) que la commande
> demande de mettre en place. Adapter `coachrun` au nom retenu.

---

## 0. Pré-requis

- **Claude Code** installé (CLI ou extension IDE).
- **Docker Desktop** installé et démarré.
- Comptes : **GitHub**, **Vercel**, **Railway**, **Strava Developers** (clé API).
- Les 4 documents de référence dans le repo : `Claude.md`, `Design.md`, `Techno.md`, `Cahier-des-charges.md`
  (les placer à la racine ou dans `docs/`).

---

## 1. La commande de lancement (à copier-coller dans Claude Code)

> Démarrer Claude Code **à la racine d'un dossier vide** déjà initialisé en git (`git init`), avec les 4
> documents de référence présents. Puis coller le prompt ci-dessous.

```text
Tu démarres le développement d'une plateforme SaaS de coaching pour la course à pied
(alternative à Nolio). Avant d'écrire la moindre ligne, lis et respecte STRICTEMENT les
documents de référence présents dans le repo : Claude.md, Design.md, Techno.md et
Cahier-des-charges.md. Ils définissent l'ADN technique, le design system, les conventions
et le périmètre. En cas de doute, suis ces documents ; ne réinvente rien.

OBJECTIF DE CETTE PREMIÈRE ITÉRATION : poser le squelette complet du projet (monorepo
front + back), le rendre lançable en local via Docker Desktop, et préparer le déploiement
Vercel (front) + Railway (back), SANS encore implémenter les fonctionnalités métier.

EXIGENCES DE STRUCTURE (monorepo, un seul dépôt git) :
- Racine du dépôt git unique, avec DEUX sous-répertoires :
    /back   → API Spring Boot 3 / Java 21 (Maven)
    /front  → Angular 17+ standalone (PWA)
- À la racine : docker-compose.yml, .env.example, README.md, .gitignore,
  .github/workflows/ci.yml, et un dossier /docs contenant les 4 documents de référence.

BACKEND (/back) :
- Spring Boot 3.2.x, Java 21, Maven. Packages : config, controller, service, entity(+enums),
  repository, dto(request/response), security, integration, scheduler, exception, util.
- BaseEntity (UUID + createdAt/updatedAt). Liquibase câblé (db.changelog-master.yaml +
  un premier changeset 001 créant la table "clubs" minimale pour valider la chaîne).
- Spring Security stateless + JWT, CORS configurable, headers de sécurité, /api comme context-path.
- Endpoint /actuator/health public. Profils dev (seed) et prod (secrets obligatoires au démarrage).
- Un endpoint de démo GET /api/public/ping renvoyant {status:"ok", version} pour tester la chaîne.
- Dockerfile multi-stage (build Maven → image JRE 21 slim).

FRONTEND (/front) :
- Angular 17+ standalone, routing lazy, PWA (service worker), LOCALE_ID fr-FR.
- Mettre en place les design tokens CSS de Design.md dans styles.scss (palette "Pace",
  fonts, espacements, composants de base .btn/.card/.badge/.form-control).
- core/ (services, models, guards, interceptors auth+error), shared/components (toast, etc.).
- environments/ : apiUrl pointant vers /api en prod, http://localhost:8080/api en dev.
- Une page d'accueil minimale qui appelle GET /api/public/ping et affiche le statut (preuve
  que front↔back communiquent).
- Dockerfile (build Angular → nginx) + nginx.conf (fallback SPA index.html).

DOCKER DESKTOP (local) :
- docker-compose.yml avec 3 services : postgres (16-alpine, healthcheck), backend (depends_on
  postgres healthy), frontend (nginx, depends_on backend). Réseau commun, volume postgres.
- Variables de dev injectées (JWT_SECRET de dev, FIELD_ENCRYPTION_KEY 64 hex de dev, MAIL_ENABLED=false).
- Objectif : `docker compose up --build` doit lever toute la stack ; front sur http://localhost:4200,
  api sur http://localhost:8081/api, et la page d'accueil doit afficher le ping OK.
- Documenter la commande dans le README.

DÉPLOIEMENT (documentation à produire dans /docs/DEPLOIEMENT.md) :
- Procédure pas-à-pas VERCEL pour le front (Root Directory = front, build, output, variable
  d'env de l'URL API) — voir l'annexe que je te fournis.
- Procédure pas-à-pas RAILWAY pour le back (service Docker depuis /back + plugin PostgreSQL +
  variables d'environnement) — voir l'annexe.
- Tableau complet des variables d'environnement (dev / prod, requis / optionnel).

CI (.github/workflows/ci.yml) :
- Job backend : mvn -B -ntp clean verify + smoke test de démarrage sur Postgres éphémère
  (valide les migrations Liquibase via /actuator/health).
- Job frontend : npm ci + npm run build (typecheck AOT).

QUALITÉ & MÉTHODE :
- Respecte les conventions de Claude.md (DTOs séparés, scoping tenant, toasts, standalone, inject()…).
- Pas d'over-engineering, pas de feature métier à ce stade : on veut un SQUELETTE qui démarre,
  se déploie et où front et back communiquent.
- À la fin : vérifie que `docker compose up --build` fonctionne, que `npm run build` et `mvn verify`
  passent. Donne-moi un récap des fichiers créés et les étapes exactes pour déployer.
- Commits clairs et atomiques. NE crée PAS de Pull Request sans que je le demande.

Commence par me proposer l'arborescence complète du monorepo et la liste des fichiers que tu vas
créer, puis exécute.
```

---

## 2. Arborescence cible du monorepo

```
coachrun/                      ← dépôt git unique
├── back/                      ← API Spring Boot
│   ├── src/main/java/com/coachrun/...
│   ├── src/main/resources/
│   │   ├── application.yml, application-dev.yml, application-prod.yml
│   │   └── db/changelog/...
│   ├── pom.xml
│   └── Dockerfile
├── front/                     ← App Angular (PWA)
│   ├── src/app/{core,features,athlete,public,shared}/
│   ├── src/{styles.scss,environments/}
│   ├── angular.json, package.json, ngsw-config.json
│   ├── nginx.conf
│   └── Dockerfile
├── docs/
│   ├── Claude.md, Design.md, Techno.md, Cahier-des-charges.md
│   └── DEPLOIEMENT.md
├── .github/workflows/ci.yml
├── docker-compose.yml
├── .env.example
├── .gitignore
└── README.md
```

> **Un seul `git init` à la racine `coachrun/`.** Front et back ne sont **pas** des sous-modules : ce sont
> de simples sous-dossiers du même dépôt (monorepo).

---

## 3. Annexe A — `docker-compose.yml` (référence Docker Desktop)

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: coachrun-postgres
    environment:
      POSTGRES_DB: coachrun
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports: ["5432:5432"]
    volumes: [postgres_data:/var/lib/postgresql/data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build: { context: ./back, dockerfile: Dockerfile }
    container_name: coachrun-backend
    depends_on:
      postgres: { condition: service_healthy }
    environment:
      SPRING_PROFILES_ACTIVE: dev
      DATABASE_URL: jdbc:postgresql://postgres:5432/coachrun
      DATABASE_USERNAME: postgres
      DATABASE_PASSWORD: postgres
      JWT_SECRET: dev-local-secret-change-in-production-min-256-bits-long!!
      FIELD_ENCRYPTION_KEY: "0000000000000000000000000000000000000000000000000000000000000000"
      MAIL_ENABLED: "false"
      CORS_ORIGINS: http://localhost:4200
    ports: ["8081:8080"]
    healthcheck:
      test: ["CMD-SHELL", "wget -q --spider http://localhost:8080/api/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 60s

  frontend:
    build:
      context: ./front
      dockerfile: Dockerfile
      args: { BUILD_CONFIG: docker }
    container_name: coachrun-frontend
    depends_on:
      backend: { condition: service_healthy }
    ports: ["4200:80"]

volumes:
  postgres_data:
```

**Lancer en local :**
```bash
docker compose up --build
# Front : http://localhost:4200   API : http://localhost:8081/api   (Swagger : /api/swagger-ui.html)
```

---

## 4. Annexe B — Déploiement **Railway** (backend + PostgreSQL)

> Railway héberge le **backend Docker** et la **base PostgreSQL**.

1. **Créer un projet** Railway → *New Project*.
2. **Ajouter PostgreSQL** : *New → Database → PostgreSQL*. Railway expose `DATABASE_URL`, `PGUSER`,
   `PGPASSWORD`, `PGHOST`, `PGPORT`, `PGDATABASE`.
3. **Ajouter le service backend** : *New → GitHub Repo* → sélectionner le dépôt.
   - **Root Directory** : `back`
   - Build : Railway détecte le `Dockerfile` dans `/back` (sinon le préciser).
4. **Variables d'environnement** du service backend (cf. tableau § 6) :
   - `SPRING_PROFILES_ACTIVE=prod`
   - `JDBC_DATABASE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`
   - `PGUSER=${{Postgres.PGUSER}}`, `PGPASSWORD=${{Postgres.PGPASSWORD}}`
   - `JWT_SECRET` (≥256 bits — `openssl rand -base64 48`)
   - `FIELD_ENCRYPTION_KEY` (64 hex — `openssl rand -hex 32`)
   - `FRONTEND_URL=https://<ton-app>.vercel.app`
   - `CORS_ORIGINS=https://<ton-app>.vercel.app`
   - intégrations (selon phase) : `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `STRAVA_WEBHOOK_VERIFY_TOKEN`,
     `RESEND_API_KEY`, `MAIL_FROM`, `S3_*`, `SENTRY_DSN`…
5. **Exposer le domaine** : *Settings → Networking → Generate Domain* → URL publique
   `https://coachrun-back.up.railway.app`. L'API sera sur `…/api`.
6. **Healthcheck** : pointer `/api/actuator/health` (Railway *Settings → Healthcheck Path*).
7. Le déploiement applique automatiquement les **migrations Liquibase** au démarrage.

---

## 5. Annexe C — Déploiement **Vercel** (frontend Angular)

> Vercel héberge **uniquement le front** (build statique Angular + PWA). Il appelle l'API Railway.

1. **Importer le dépôt** sur Vercel → *Add New → Project* → sélectionner le repo GitHub.
2. **Configurer le projet** :
   - **Root Directory** : `front`
   - **Framework Preset** : Angular (ou *Other*).
   - **Build Command** : `npm run build` (= `ng build --configuration=production`).
   - **Output Directory** : `dist/<nom-app>/browser` (Angular 17) — vérifier le nom exact dans `angular.json`.
3. **Variable d'environnement** (côté front) : l'URL de l'API.
   - Soit via `environment.prod.ts` (committé) : `apiUrl: 'https://coachrun-back.up.railway.app/api'`.
   - Soit via une variable Vercel injectée au build _(à valider selon la config Angular)_.
4. **Rewrites SPA** : ajouter `front/vercel.json` :
   ```json
   { "rewrites": [ { "source": "/(.*)", "destination": "/index.html" } ] }
   ```
5. **Déployer** → URL `https://<ton-app>.vercel.app`.
6. **Boucler la config CORS** : reporter cette URL dans `CORS_ORIGINS` et `FRONTEND_URL` côté Railway,
   puis redéployer le backend.

**Ordre recommandé : Railway d'abord** (pour obtenir l'URL API) → configurer le front avec cette URL →
déployer Vercel → reporter l'URL Vercel dans le CORS Railway.

---

## 6. Annexe D — Tableau des variables d'environnement

> `[PROD-REQUIS]` obligatoire en prod (l'app refuse de démarrer sinon) · `[DÉFAUT]` valeur par défaut ·
> `[OPT]` optionnel selon les fonctionnalités activées.

| Variable | Portée | Description | Exemple / génération |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | back | `dev` ou `prod` | `prod` |
| `JDBC_DATABASE_URL` | back | URL JDBC PostgreSQL [PROD-REQUIS] | `jdbc:postgresql://host:5432/coachrun` |
| `PGUSER` / `PGPASSWORD` | back | Identifiants DB [PROD-REQUIS] | fournis par Railway |
| `JWT_SECRET` | back | Secret JWT ≥256 bits [PROD-REQUIS] | `openssl rand -base64 48` |
| `FIELD_ENCRYPTION_KEY` | back | Clé AES-256, 64 hex [PROD-REQUIS] | `openssl rand -hex 32` |
| `FRONTEND_URL` | back | URL du front (liens emails) [PROD-REQUIS] | `https://coachrun.vercel.app` |
| `CORS_ORIGINS` | back | Origines autorisées (CSV) [PROD-REQUIS] | `https://coachrun.vercel.app` |
| `MAIL_ENABLED` | back | Active l'envoi d'emails [DÉFAUT false] | `true` |
| `RESEND_API_KEY` | back | Clé API Resend [OPT si MAIL_ENABLED] | `re_...` |
| `MAIL_FROM` | back | Adresse expéditrice vérifiée [OPT] | `no-reply@coachrun.fr` |
| `STRAVA_CLIENT_ID` | back | ID app Strava [OPT — module Intégrations] | `12345` |
| `STRAVA_CLIENT_SECRET` | back | Secret app Strava [OPT] | `...` |
| `STRAVA_WEBHOOK_VERIFY_TOKEN` | back | Token de vérif webhook Strava [OPT] | chaîne aléatoire |
| `S3_ENDPOINT` / `S3_BUCKET` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_PUBLIC_URL` | back | Stockage fichiers FIT/GPX [OPT] | R2 / S3 |
| `STORAGE_TYPE` | back | `local` ou `s3` [DÉFAUT local] | `s3` |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | back | Push WebPush [OPT — module Communication] | générées via web-push |
| `SENTRY_DSN` | back+front | Monitoring erreurs [OPT] | `https://...@sentry.io/...` |
| `apiUrl` (environment.prod.ts) | front | URL de l'API | `https://coachrun-back.up.railway.app/api` |

> Le fichier `.env.example` à la racine doit reprendre **toutes** ces variables, commentées, comme modèle.

---

## 7. Itérations suivantes (après le squelette)

Une fois le squelette déployé et fonctionnel, relancer Claude Code, **phase par phase** selon le
Cahier-des-charges.md (§ 9) :
1. **Phase 1 — MVP** : athlètes + invitation, calendrier + séances structurées, import Strava + rapprochement, vue athlète « séance du jour » + RPE, notifications email.
2. **Phase 2** : bibliothèque & plans, charge & graphes, messagerie, push, objectifs.
3. **Phase 3** : wellness, facturation, Garmin/Coros, multi-coach club.

Prompt-type pour une feature :
```text
Implémente la fonctionnalité [X] décrite dans Cahier-des-charges.md § [N], en respectant
Claude.md (conventions, scoping tenant, DTOs, machine à états) et Design.md (composants, tokens).
Crée la migration Liquibase, l'entité, les DTOs, le service, le controller (route scopée + @RequiresModule),
puis le service Angular et les composants standalone. Ajoute les toasts et les états (loading/vide/erreur).
Vérifie `mvn verify` et `npm run build`. Incrémente la version. Ne crée pas de PR sans me le demander.
```

---

*Fin du guide de lancement — adapter `coachrun`, les URLs et les noms de domaine au projet réel.*
