# Déploiement — DARI Lab

> Front sur **Vercel**, back + PostgreSQL sur **Railway**.
> **Ordre recommandé : Railway d'abord** (pour obtenir l'URL de l'API) → configurer le front avec
> cette URL → déployer Vercel → reporter l'URL du front dans le CORS Railway.

## Domaine (en place)

| Élément | Valeur |
|---|---|
| Registrar / DNS | **OVH** (zone `darilab.app`) |
| URL canonique | **`https://www.darilab.app`** |
| Apex | `darilab.app` → redirection 308 vers `www` |

Zone DNS (hors NS/MX/SPF gérés par OVH) :

| Sous-domaine | Type | Cible |
|---|---|---|
| `@` | A | `216.198.79.1` (IP Vercel ; `76.76.21.21` reste valide) |
| `www` | CNAME | cible CNAME propre au projet Vercel (`*.vercel-dns-0NN.com.`) |

> ⚠️ L'onglet **« Redirection »** d'OVH doit rester **vide** : ses entrées recréent des
> enregistrements `A` vers l'IP de parking OVH (`213.186.33.5`) et cassent la configuration Vercel.

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
   FRONTEND_URL=https://www.darilab.app
   CORS_ORIGINS=https://www.darilab.app,https://darilab.app
   ```
   > **URL canonique : `https://www.darilab.app`** (l'apex `darilab.app` y redirige en 308).
   > C'est elle qui doit figurer dans `FRONTEND_URL` (liens des e-mails) et dans le redirect Strava.
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
| `FRONTEND_URL` | back | URL du front (liens emails) **[PROD-REQUIS]** | `https://www.darilab.app` |
| `CORS_ORIGINS` | back | Origines autorisées (CSV) **[PROD-REQUIS]** | `https://www.darilab.app,https://darilab.app` |
| `MAIL_ENABLED` | back | Active l'envoi d'emails **[DÉFAUT false]** | `true` |
| `RESEND_API_KEY` | back | Clé API Resend **[OPT]** | `re_...` |
| `MAIL_FROM` | back | Adresse expéditrice vérifiée **[OPT]** | `Darilab <no-reply@darilab.app>` |
| `STRAVA_CLIENT_ID` / `STRAVA_CLIENT_SECRET` / `STRAVA_WEBHOOK_VERIFY_TOKEN` | back | App Strava **[OPT — Intégrations]** | console Strava |
| `GARMIN_*` / `COROS_*` | back | OAuth Garmin / Coros **[OPT]** | — |
| `STORAGE_TYPE` | back | `local` ou `s3` **[DÉFAUT local]** | `s3` |
| `S3_ENDPOINT` / `S3_BUCKET` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_PUBLIC_URL` | back | Stockage FIT/GPX **[OPT]** | R2 / S3 |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` / `VAPID_SUBJECT` | back | Push WebPush **[PROD-REQUIS]** — « séance planifiée » et « commentaire du coach » partent en push **sans repli e-mail** : sans clés, ces notifications ne partent nulle part, et aucun appareil ne peut même s'abonner. En local, une paire est fabriquée automatiquement (cf. § Notifications push) | `npx web-push generate-vapid-keys` |
| `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET` | back | Paiements **[OPT — Billing]** | console Stripe |
| `REGISTRATION_MODE` | back | `invite` (code exigé) ou `open` **[PROD-REQUIS — défaut `invite` en prod]** | `invite` |
| `REGISTRATION_INVITE_CODE` | back | Code partagé de la cohorte **[REQUIS si mode=invite]** | `BETA-2026-XXXX` |
| `RATE_LIMIT_TRUSTED_PROXY_HOPS` | back | Relais de confiance **devant l'API** **[DÉFAUT 1]**. Le navigateur appelle l'API directement (le front est sur Vercel, l'API sur Railway — d'où le CORS) : **1** est la bonne valeur. Annoncer plus fait compter l'adresse du proxy, la même pour tout le monde, et toute la plateforme partage alors un seul compteur | `1` |
| `REMINDERS_CRON` | back | Heure du point de programme du soir **[DÉFAUT 21 h]**, lu dans `APP_TIMEZONE` | `0 0 21 * * *` |
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

---

## 5. Notifications push

La chaîne compte **quatre maillons**, et il suffit qu'un seul manque pour que rien n'arrive —
sans le moindre message. Les voici dans l'ordre où il faut les vérifier.

| Maillon | Comment savoir | S'il manque |
|---|---|---|
| **Service worker enregistré** (front) | `navigator.serviceWorker.getRegistration()` dans la console | `ng serve` n'en produit pas : **aucun push ne peut arriver en développement**. Utiliser `npm run start:pwa` (cf. ci-dessous) |
| **Clés VAPID** (back) | ligne `Push VAPID : …` au démarrage du serveur | Le navigateur ne peut même pas s'abonner. En prod : poser `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` |
| **Appareil abonné** | Notifications › Mes appareils (coach), Profil (athlète) | Activer les notifications sur l'appareil. Sur iPhone, l'application doit être **installée** sur l'écran d'accueil |
| **Remise acceptée** | bouton **« Tester les notifications »** | Le message dit l'appareil et la cause : signature refusée, abonnement expiré, service injoignable |

Le bouton de test envoie **en synchrone** et rapporte ce que le service de push a réellement
accepté. C'est le point d'entrée du diagnostic : il distingue les quatre causes ci-dessus,
là où « rien ne s'affiche sur mon téléphone » les confond toutes.

### Éprouver le push en local

`ng serve` ne sert pas `ngsw-worker.js` : les notifications y sont structurellement hors de
portée. Deux façons d'avoir la chaîne complète en local :

```bash
# a) Pile Docker : le front y est déjà servi par nginx depuis un build de production,
#    service worker compris. Le back tourne en profil dev, donc génère ses clés.
docker compose up --build

# b) Sans Docker, en gardant le code du poste :
cd back && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # génère .vapid-dev.json au 1er lancement
cd front && npm run start:pwa                                     # build AVEC service worker, servi sur :4200
```

Puis : ouvrir http://localhost:4200 (contexte sécurisé, `localhost` suffit), activer les
notifications depuis Profil, et cliquer « Tester les notifications ».

Les clés de développement sont fabriquées au premier démarrage et **conservées** dans
`back/.vapid-dev.json` (non versionné). La persistance n'est pas du confort : un abonnement de
navigateur est lié à la clé publique avec laquelle il a été créé, donc une paire régénérée à
chaque redémarrage couperait les abonnements du poste sans rien dire. En production, aucune
génération automatique : une identité de serveur qui changerait au redéploiement couperait les
abonnements de tous les athlètes.
