# CoachRun — Plateforme de coaching course à pied

> Renommer `CoachRun` selon le nom retenu. SaaS de coaching running (alternative à Nolio) :
> le coach prescrit des plans, l'athlète suit ses séances (synchronisées depuis sa montre), tous deux
> communiquent et pilotent la performance. Monorepo **Angular (front) + Spring Boot (back)**.

---

## 🧱 Stack

| Couche | Techno |
|---|---|
| Frontend | Angular 17+ (standalone, PWA), TypeScript |
| Backend | Spring Boot 3, Java 21, API REST |
| Base de données | PostgreSQL + Liquibase (migrations) |
| Auth | JWT + liens magiques (athlètes) |
| Intégrations | Strava / Garmin / Coros, FIT/GPX |
| Hébergement | Front : Vercel · Back + DB : Railway |

Documentation complète dans [`/docs`](./docs) : `Claude.md` (conventions IA), `Design.md` (design system),
`Techno.md` (référence technique), `Cahier-des-charges.md` (périmètre fonctionnel).

---

## 📁 Structure du dépôt (monorepo)

```
.
├── back/      # API Spring Boot (Java 21, Maven)
├── front/     # App Angular (PWA)
├── docs/      # Documents de référence (CDC, design, techno…)
├── docker-compose.yml
├── .env.example
└── .github/workflows/ci.yml
```

---

## 🚀 Démarrage rapide (Docker Desktop)

Pré-requis : **Docker Desktop** démarré.

```bash
git clone <url-du-repo> && cd coachrun
docker compose up --build
```

| Service | URL |
|---|---|
| Frontend | http://localhost:4200 |
| API | http://localhost:8081/api |
| Swagger | http://localhost:8081/api/swagger-ui.html |
| PostgreSQL | localhost:5432 (`postgres` / `postgres`) |

La page d'accueil appelle `GET /api/public/ping` : si le statut s'affiche, front et back communiquent. ✅

---

## 🎬 Comptes de démo

En profil `dev`, un jeu de données complet est chargé automatiquement. Connexion (mot de passe `password123`) :

| Rôle | Email |
|---|---|
| Admin plateforme | `admin@coachrun.fr` → `/admin` |
| Responsable club | `demo@coachrun.fr` → `/app` |
| Coach | `coach@coachrun.fr` → `/app` |
| Athlète | `athlete@coachrun.fr` → `/athlete/today` |

Réinitialiser l'état de démo (bouton « Réinitialiser la démo » dans `/admin`, ou `POST /api/admin/demo/reset`) :
comptes, contenu et **procédure RAZ** détaillés dans [`docs/DEMO.md`](./docs/DEMO.md).

---

## 🛠️ Développement local (sans Docker)

### Backend
```bash
cd back
# Lancer un PostgreSQL local (ou via docker compose up postgres)
./mvnw spring-boot:run        # profil dev par défaut, http://localhost:8080/api
```

### Frontend
```bash
cd front
npm install
npm start                     # http://localhost:4200 (proxy vers http://localhost:8080/api)
```

---

## ✅ Qualité / CI

```bash
# Backend : build + tests + smoke test démarrage (migrations Liquibase)
cd back && ./mvnw -B -ntp clean verify

# Frontend : build de production (typecheck AOT)
cd front && npm run build
```

La CI GitHub Actions (`.github/workflows/ci.yml`) exécute ces deux jobs à chaque push / PR.

---

## 🔐 Variables d'environnement

Copier `.env.example` en `.env` (local) ou configurer les variables dans Railway/Vercel.
Tableau complet et procédures de déploiement : [`docs/DEPLOIEMENT.md`](./docs/DEPLOIEMENT.md).

Clés critiques en production : `JWT_SECRET`, `FIELD_ENCRYPTION_KEY` (64 hex), `JDBC_DATABASE_URL`,
`FRONTEND_URL`, `CORS_ORIGINS`. **L'app refuse de démarrer en prod si les secrets sont laissés par défaut.**

---

## ☁️ Déploiement

- **Backend + PostgreSQL → Railway** (service Docker depuis `/back` + plugin PostgreSQL).
- **Frontend → Vercel** (Root Directory = `front`, build Angular, rewrites SPA).

Procédure pas-à-pas (ordre, variables, CORS) : [`docs/DEPLOIEMENT.md`](./docs/DEPLOIEMENT.md).

---

## 🗺️ Roadmap (cf. Cahier des charges § 9)

- **Phase 1 — MVP** : athlètes + invitation · calendrier + séances structurées · import Strava + rapprochement · vue athlète « séance du jour » + RPE · notifications email.
- **Phase 2** : bibliothèque & plans périodisés · charge & graphes · messagerie · push · objectifs.
- **Phase 3** : wellness · facturation coaching privé · Garmin/Coros · multi-coach club.

---

## 🤝 Conventions

Voir [`docs/Claude.md`](./docs/Claude.md) : entités héritant de `BaseEntity`, DTOs Request/Response séparés,
scoping multi-tenant systématique, composants Angular standalone + `inject()`, feedback toast, migrations
Liquibase pour tout changement de schéma. **Nommage anglais dans le code, libellés français dans l'UI.**

---

## 📄 Licence

_(à définir — privée par défaut)_
