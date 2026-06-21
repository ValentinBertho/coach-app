# Déploiement — CoachRun

> Front sur **Vercel**, back + PostgreSQL sur **Railway**.
> **Ordre recommandé : Railway d'abord** (pour obtenir l'URL de l'API) → configurer le front avec
> cette URL → déployer Vercel → reporter l'URL Vercel dans le CORS Railway.

---

## 1. Railway — backend (Docker) + PostgreSQL

1. **Créer un projet** Railway → *New Project*.
2. **Ajouter PostgreSQL** : *New → Database → PostgreSQL*. Railway expose `PGHOST`, `PGPORT`,
   `PGDATABASE`, `PGUSER`, `PGPASSWORD`, `DATABASE_URL`.
3. **Ajouter le service backend** : *New → GitHub Repo* → sélectionner le dépôt.
   - **Root Directory** : `back`
   - Railway détecte le `Dockerfile` dans `/back` (multi-stage Maven → JRE 21).
4. **Variables d'environnement** du service backend (cf. tableau § 3) :
   ```
   SPRING_PROFILES_ACTIVE=prod
   JDBC_DATABASE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
   PGUSER=${{Postgres.PGUSER}}
   PGPASSWORD=${{Postgres.PGPASSWORD}}
   JWT_SECRET=<openssl rand -base64 48>
   FIELD_ENCRYPTION_KEY=<openssl rand -hex 32>
   FRONTEND_URL=https://<ton-app>.vercel.app
   CORS_ORIGINS=https://<ton-app>.vercel.app
   ```
   > En profil `prod`, l'application **refuse de démarrer** si `JWT_SECRET` / `FIELD_ENCRYPTION_KEY`
   > sont absents ou laissés aux valeurs de développement.
5. **Exposer le domaine** : *Settings → Networking → Generate Domain* →
   `https://coachrun-back.up.railway.app`. L'API sera servie sous `…/api`.
6. **Healthcheck** : *Settings → Healthcheck Path* → `/api/actuator/health`.
7. Au démarrage, **Liquibase** applique automatiquement les migrations.

---

## 2. Vercel — frontend Angular (PWA)

1. **Importer le dépôt** → *Add New → Project* → repo GitHub.
2. **Configurer le projet** :
   - **Root Directory** : `front`
   - **Framework Preset** : Angular (ou *Other*).
   - **Build Command** : `npm run build`
   - **Output Directory** : `dist/front/browser`
3. **Brancher le front sur l'API** — deux options :
   - **Option A (recommandée, sans CORS)** — proxifier `/api` vers Railway via `front/vercel.json`.
     Garder `apiUrl: '/api'` dans `environment.ts` et ajouter une règle de rewrite **avant** le
     fallback SPA :
     ```json
     {
       "rewrites": [
         { "source": "/api/(.*)", "destination": "https://coachrun-back.up.railway.app/api/$1" },
         { "source": "/(.*)", "destination": "/index.html" }
       ]
     }
     ```
   - **Option B (appel direct + CORS)** — mettre l'URL complète dans
     `front/src/environments/environment.ts` :
     ```ts
     export const environment = { production: true, apiUrl: 'https://coachrun-back.up.railway.app/api' };
     ```
     et s'assurer que `CORS_ORIGINS` (Railway) contient l'URL Vercel.
4. Le `front/vercel.json` fourni contient déjà le **fallback SPA** (`/(.*) → /index.html`).
5. **Déployer** → `https://<ton-app>.vercel.app`.
6. **Boucler le CORS** : reporter l'URL Vercel dans `CORS_ORIGINS` et `FRONTEND_URL` côté Railway,
   puis redéployer le backend.

---

## 3. Variables d'environnement

> `[PROD-REQUIS]` obligatoire en prod (l'app refuse de démarrer sinon) · `[DÉFAUT]` valeur par défaut ·
> `[OPT]` optionnel selon les modules activés. Modèle complet : [`.env.example`](../.env.example).

| Variable | Portée | Description | Exemple / génération |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | back | `dev` ou `prod` | `prod` |
| `JDBC_DATABASE_URL` | back | URL JDBC PostgreSQL **[PROD-REQUIS]** | `jdbc:postgresql://host:5432/coachrun` |
| `PGUSER` / `PGPASSWORD` | back | Identifiants DB **[PROD-REQUIS]** | fournis par Railway |
| `JWT_SECRET` | back | Secret JWT ≥512 bits **[PROD-REQUIS]** | `openssl rand -base64 48` |
| `JWT_ACCESS_TTL` | back | Durée de l'access token (s) **[DÉFAUT 900]** | `900` |
| `FIELD_ENCRYPTION_KEY` | back | Clé AES-256, 64 hex **[PROD-REQUIS]** | `openssl rand -hex 32` |
| `FRONTEND_URL` | back | URL du front (liens emails) **[PROD-REQUIS]** | `https://coachrun.vercel.app` |
| `CORS_ORIGINS` | back | Origines autorisées (CSV) **[PROD-REQUIS]** | `https://coachrun.vercel.app` |
| `MAIL_ENABLED` | back | Active l'envoi d'emails **[DÉFAUT false]** | `true` |
| `RESEND_API_KEY` | back | Clé API Resend **[OPT]** | `re_...` |
| `MAIL_FROM` | back | Adresse expéditrice vérifiée **[OPT]** | `no-reply@coachrun.fr` |
| `STRAVA_CLIENT_ID` / `STRAVA_CLIENT_SECRET` / `STRAVA_WEBHOOK_VERIFY_TOKEN` | back | App Strava **[OPT — Intégrations]** | console Strava |
| `GARMIN_*` / `COROS_*` | back | OAuth Garmin / Coros **[OPT]** | — |
| `STORAGE_TYPE` | back | `local` ou `s3` **[DÉFAUT local]** | `s3` |
| `S3_ENDPOINT` / `S3_BUCKET` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_PUBLIC_URL` | back | Stockage FIT/GPX **[OPT]** | R2 / S3 |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | back | Push WebPush **[OPT — Communication]** | `web-push generate-vapid-keys` |
| `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET` | back | Paiements **[OPT — Billing]** | console Stripe |
| `SENTRY_DSN` | back + front | Monitoring erreurs **[OPT]** | `https://...@sentry.io/...` |
| `apiUrl` (`environment.ts`) | front | URL de l'API | `/api` ou URL Railway |

---

## 4. Local (Docker Desktop)

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| Frontend | http://localhost:4200 |
| API | http://localhost:8081/api |
| Swagger | http://localhost:8081/api/swagger-ui.html |
| Health | http://localhost:8081/api/actuator/health |
| PostgreSQL | localhost:5432 (`postgres` / `postgres`) |

La page d'accueil appelle `GET /api/public/ping` ; si le statut « En ligne » s'affiche,
front et back communiquent. ✅
