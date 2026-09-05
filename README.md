# DARI Lab Training — plateforme de coaching physiologique

> Application SaaS de coaching **course à pied + préparation physique**, pilotée par la
> physiologie (seuils lactiques, VDOT, domaines d'intensité, 1RM, charge interne).
> Le coach prescrit des séances **en fourchettes** calculées pour chaque athlète ; l'athlète
> suit son programme, renseigne ses retours, et les deux communiquent en temps réel.
>
> Monorepo **Angular 17 (front) + Spring Boot 3 / Java 21 (back) + PostgreSQL**.
> Construit sur la base technique CoachRun, réorienté selon le cahier des charges DARI Lab
> (la stack Next.js/Supabase du CDC d'origine est volontairement remplacée par Spring/Angular).

---

## Sommaire

- [Aperçu fonctionnel](#aperçu-fonctionnel)
- [Moteurs de calcul](#moteurs-de-calcul-le-cœur-métier)
- [Stack technique](#stack-technique)
- [Démarrage rapide (Docker)](#démarrage-rapide-docker)
- [Comptes de démonstration](#comptes-de-démonstration)
- [Commandes Docker utiles](#commandes-docker-utiles)
- [Base de données](#base-de-données-migrations--réinitialisation)
- [Problèmes courants](#problèmes-courants)
- [Développement local](#développement-local-sans-docker)
- [Structure du dépôt](#structure-du-dépôt)
- [Qualité, tests & CI](#qualité-tests--ci)
- [Sécurité & RGPD](#sécurité--rgpd)
- [Variables d'environnement](#variables-denvironnement)
- [Déploiement](#déploiement)
- [Limites connues & pistes](#limites-connues--pistes-damélioration)
- [Documentation](#documentation)
- [Conventions](#conventions)

---

## Aperçu fonctionnel

L'application sert **trois rôles** : administrateur plateforme, coach (responsable de club ou
assistant), et athlète (PWA mobile).

### Module course à pied
- **Profil physiologique** par athlète : performances de référence → **VDOT**, allures
  d'équivalence **et allures d'entraînement** (easy ≈ 70 % VDOT, seuil ≈ 88 %), seuils
  **LT1/LT2** et **vitesse critique** saisis directement (ou calculés par le test de VC, FC
  moyenne des efforts comprise), **domaines d'intensité** (1/2/3), FC de seuil.
- **Bibliothèque de séances** + catégories **et sous-catégories**, **éditeur de structure unique**
  (échauffement / corps / retour au calme) en **fourchettes** (% LT1/LT2/VC/allures) avec
  **calculateur live** par athlète. Chaque bloc se prescrit **par zone** du club ou, d'un clic, en
  **fourchette de % sur mesure** (« 6 × 1000 à 102–106 % de VC ») quand aucune bande nommée ne
  convient. Identité (nom, titre, catégorie) éditable dans l'éditeur ;
  volumes saisis dans **l'unité de son choix** (sec / min / h · m / km) ; blocs pré-remplis ;
  **éducatifs de course** (gammes technique/amplitude) attachables aux blocs. Affichage
  **cartes compactes ↔ liste dense** avec recherche et filtre par catégorie.
- **Calendrier** multi-types (course / force / test / objectif / indispo) avec drag & drop et
  snapshot figé de la prescription. Une séance construite directement dans le calendrier
  s'**enregistre comme nouveau modèle** de bibliothèque en un geste.
  **Bibliothèque latérale repliable** (semaine pleine largeur) ;
  les actions d'écriture (planifier, dupliquer la semaine, mésocycle) sont **désactivées sur un
  athlète en lecture seule** (cohérent avec la permission `write`).
- **Zones d'entraînement paramétrables** : le coach nomme ses zones, leurs métriques et leurs
  règles de calcul (ancre + %), et peut entretenir **plusieurs modèles de zones** (route / trail,
  débutant / confirmé…) qu'il applique athlète par athlète ; les cibles se recalculent seules
  quand une valeur de référence change.
- **Dashboard coach** : tableaux Route/Trail, **pastilles de forme** (fatigue + douleur, jamais RPE),
  portée mes athlètes / privés / club.
- **Portail athlète** (PWA, offline-friendly) : ouvre sur un **calendrier mensuel visuel** — une
  case par jour, durée et distance de chaque séance, couleur par type, sortie réalisée
  distinguée du prescrit ; un jour se touche pour son détail. Puis séance du jour **avec cibles en fourchettes**
  (allure/FC/RPE) et éducatifs liés, retour (RPE / fatigue / douleur / commentaire), **déplacement**
  de séance (jamais de modification/suppression), **mes activités** (saisie manuelle + import FIT/GPX/TCX),
  **mes objectifs** (CRUD A/B/C) et **connexion de sa montre** (Strava) directement côté athlète.
  L'agenda se lit en **prévu / réalisé / les deux** : les sorties qui n'étaient pas au programme
  y apparaissent, marquées comme telles. Chaque sortie s'ouvre sur son tracé, son temps en zone
  et son **détail tour par tour** — les tours de la montre quand elle en a pris (les répétitions
  d'un fractionné), des splits kilométriques calculés sinon. L'athlète **corrige, note et
  supprime** ses sorties : RPE et mot au coach vivent sur la sortie elle-même, seul endroit
  possible quand aucune séance prescrite ne les porte (les mesures d'une montre, elles, restent
  en lecture seule).

### Module préparation physique (force)
- **Bibliothèque d'exercices** (catégories, groupes musculaires, matériel, vidéo, progression/régression).
- **Éditeur de structure de séance** : blocs typés + **formats avancés** (Classique, EMOM, AMRAP,
  For Time, Circuit, Isométrie, Pliométrie) et **types de série** (drop-set, super-set, myo-reps,
  cluster, iso) avec prescription complète (charge **et** effort indépendants, tempo, repos).
- **Calcul de charge (1RM)** : méthode **Nuzzo** par défaut + Epley / Brzycki / RIR-based,
  zones de travail Lacourpaille.
- **Tests 1RM** (4 protocoles : 1RM direct, rep-test 3–5, AMRAP, isométrie MVC) → mise à jour
  auto du profil 1RM (`tested` prévaut sur l'estimé).
- **Cycles de force** (progression multi-semaines) assignables au calendrier.
- **Progression automatique + alertes coach** (règles DARI Lab §6.7 / §6.8).
- **Suivi & analyse** : courbes e1RM, **charge mécanique / métabolique (UA)**.

### Communication & données
- **Messagerie temps réel** (Server-Sent Events) coach ↔ athlète, avec **pièces jointes** (images / PDF).
- **Objectifs A/B/C** (gérables par le coach **et** par l'athlète) et **indisponibilités**
  (blessure / maladie / vacances).
- **Sync Strava** (OAuth) **initiée par l'athlète** (l'intégration est d'abord côté athlète, CDC §12),
  import automatique des activités avec déduplication ; le coach voit l'état en lecture seule. Import
  fichier **FIT / GPX / TCX** — le FIT est le format natif Garmin et COROS, décodé sans dépendance
  externe (totaux de la montre, tours et vitesse mesurée compris) — et **saisie manuelle** d'une
  sortie par l'athlète. La **synchronisation automatique** Garmin / COROS reste à venir : demandes
  d'accès déposées, l'import FIT en tient lieu en attendant.
- **Export PDF** du programme d'un athlète.
- Notifications e-mail (Resend) et push (Web Push / VAPID).

---

## Moteurs de calcul (le cœur métier)

Toute la physiologie vit dans des **moteurs Java purs** (`com.coachrun.engine`), testés
unitairement et **source de vérité** (recalcul à la sauvegarde, équivalent des triggers Supabase).

| Moteur | Entrées → sorties | Référence |
|---|---|---|
| `VdotEngine` | performances → VDOT + allures | Daniels / Riegel `T2 = T1·(D2/D1)^1.06` |
| `LactateThresholdEngine` | paliers lactate → LT1 / LT2 | LT1 = baseline + 0,5 ; LT2 = Dmax modifié |
| `IntensityDomainEngine` | vitesse/FC + profil → domaine 1/2/3 | priorité physio (LT1/LT2), fallback FC |
| `SessionCalculatorEngine` | bloc + profil → allure/FC/RPE/durée | prescription en **fourchettes** min–max |
| `LoadEngine` | sRPE (RPE×durée) → ATL/CTL/ratio/monotonie | ACWR unifié course + force |
| `FormStatusEngine` | fatigue + douleur → statut de forme | **jamais** le RPE (principe DARI Lab) |
| `OneRmEngine` | charge/reps/(rir\|rpe) → e1RM + zones | **Nuzzo** par défaut (constante 104.9) |
| `StrengthChargeEngine` | prescription + 1RM → charges cibles (kg) | arrondi au palier 2,5 kg |
| `StrengthLoadEngine` | séries réalisées → charge méca / métab (UA) | méca = Σ(charge×reps×%1RM)/100 ; métab = sRPE |
| `ProgressionEngine` | résultats vs prescription → suggestion + alertes | +2,5/+5 kg ; alertes douleur/RPE/RIR/charge |
| `PaceUtil` | conversions d'allure | utilitaire |

---

## Stack technique

| Couche | Techno |
|---|---|
| **Frontend** | Angular 17 (standalone components, signals, control-flow `@if`/`@for`, OnPush), PWA, TypeScript 5.4, Leaflet (cartes) |
| **Backend** | Spring Boot 3.2.5, Java 21, API REST (~295 endpoints), Springdoc/OpenAPI |
| **Base de données** | PostgreSQL 18 · **Liquibase** (85 changelogs versionnés, 96 changesets) |
| **Auth** | JWT (access tokens) + liens magiques d'invitation athlète · `@PreAuthorize` multi-tenant |
| **Sécurité** | AES-256-GCM (données santé + jetons OAuth chiffrés au repos), CSP, CORS allowlist, rate-limiting |
| **Intégrations** | Strava (OAuth), import FIT/GPX/TCX (décodeurs maison), e-mail Resend, Web Push (VAPID), export PDF (OpenPDF) |
| **Temps réel** | Server-Sent Events (messagerie) |
| **CI/CD** | GitHub Actions · Docker · Railway (back + DB) · Vercel (front) |

---

## Démarrage rapide (Docker)

> Trois conteneurs, une commande, **aucun fichier de configuration à créer**. Ni JDK, ni Node,
> ni PostgreSQL à installer sur le poste : tout est construit dans les images.

### 1. Prérequis

| Outil | Version | Vérifier |
|---|---|---|
| **Docker Engine** (ou Docker Desktop) | ≥ 24, **démon démarré** | `docker version` |
| **Docker Compose v2** (plugin `docker compose`, pas `docker-compose`) | ≥ 2.20 | `docker compose version` |

Prévoir ~**4 Go de RAM** disponibles et ~**3 Go de disque** : le premier lancement compile le
backend avec Maven et le frontend avec Angular *à l'intérieur* des images.

### 2. Cloner le dépôt

```bash
git clone <url-du-repo>
cd coach-app
```

### 3. Variables d'environnement : rien à créer

`docker-compose.yml` porte lui-même tout ce dont la stack locale a besoin (base, secrets de
développement, CORS, e-mail désactivé). **`.env` n'est ni requis ni lu** par la stack Docker :
`.env.example` sert au développement **sans** Docker et au déploiement Railway/Vercel.

Seuls les **ports publiés sur la machine** sont surchargeables, si l'un d'eux est déjà pris :

```bash
FRONTEND_PORT=4300 BACKEND_PORT=8082 POSTGRES_PORT=55432 docker compose up --build
```

### 4. Lancer

```bash
docker compose up --build          # au premier plan (Ctrl+C arrête la stack)
docker compose up --build -d       # ou en arrière-plan
```

Le premier lancement prend **5 à 10 minutes** (téléchargement des images, `mvn package`,
`npm ci` + build Angular). Les suivants démarrent en quelques secondes.

L'ordre est géré par Compose, sans intervention :

1. **postgres** démarre et devient *healthy* (`pg_isready`) ;
2. **backend** attend PostgreSQL, applique les **migrations Liquibase** puis génère le
   **jeu de données de démonstration** (profil `dev`) ; il devient *healthy* quand
   `/api/actuator/health` répond `UP` ;
3. **frontend** (nginx) démarre une fois le backend sain et sert le build Angular, en
   proxifiant `/api/` vers le backend par le réseau interne `coachrun`.

Le backend est prêt quand les journaux affichent :

```
[seed dev] Jeu de démo prêt — connexion : demo@coachrun.fr / password123
Darilab 0.2.0 démarré — profil actif : dev · front : http://localhost:4200
```

### 5. Accéder à l'application

| Service | URL | Remarque |
|---|---|---|
| **Frontend** | http://localhost:4200 | c'est **la seule URL à ouvrir** dans le navigateur |
| API (accès direct) | http://localhost:8081/api | utile pour `curl` / Postman |
| Ping API | http://localhost:8081/api/public/ping | → `{"status":"ok","version":"0.2.0"}` |
| Health | http://localhost:8081/api/actuator/health | → `{"status":"UP",…}` |
| Swagger UI | http://localhost:8081/api/swagger-ui.html | ouvert en profil `dev` |
| PostgreSQL | `localhost:5432` (`postgres` / `postgres`, base `coachrun`) | |

> Le navigateur n'appelle **jamais** le port 8081 : le front est construit avec `apiUrl = '/api'`
> et nginx relaie vers le backend. Le port 8081 n'existe que pour vos propres appels directs.

> 🐞 **Poser des points d'arrêt** (dans le conteneur, ou depuis IntelliJ / VS Code) :
> [`docs/DEBUG.md`](./docs/DEBUG.md).

---

## Comptes de démonstration

Mot de passe commun : **`password123`**

| Rôle | Email | Destination |
|---|---|---|
| Admin plateforme | `admin@coachrun.fr` | `/admin` |
| Responsable de club | `demo@coachrun.fr` | `/app` |
| Coach assistant | `coach@coachrun.fr` | `/app` |
| Athlète | `athlete@coachrun.fr` | `/athlete/today` |

> Le jeu de démo est **déterministe** (graine fixe) : profils physio, tests lactate, séances course
> et force structurées, cycles, tests 1RM, charge, objectifs et indisponibilités. Il est produit par
> `DemoSeedService` (profil `dev`, `app.seed.enabled`) et réinitialisable via `DemoResetService`.

---

## Commandes Docker utiles

Toutes se lancent **à la racine du dépôt** (là où se trouve `docker-compose.yml`).

```bash
# Cycle de vie
docker compose up --build          # construire (si besoin) puis démarrer, au premier plan
docker compose up -d               # démarrer en arrière-plan
docker compose stop                # arrêter les conteneurs (données conservées)
docker compose start               # les redémarrer
docker compose restart backend     # redémarrer un seul service
docker compose down                # arrêter ET supprimer les conteneurs (données conservées)
docker compose down -v             # ⚠ idem + SUPPRIME la base (volume postgres_data)

# Observation
docker compose ps                  # état et santé des trois services
docker compose logs -f             # journaux de toute la stack
docker compose logs -f backend     # journaux du backend seul
docker compose top                 # processus de chaque conteneur

# Reconstruction après modification du code
docker compose up -d --build backend    # rebuild + redémarrage du backend
docker compose build --no-cache front   # rebuild complet du front (cache ignoré)

# Entrer dans un conteneur
docker compose exec backend sh
docker compose exec postgres psql -U postgres -d coachrun
```

> Les trois services s'appellent **`postgres`**, **`backend`** et **`frontend`**.

---

## Base de données (migrations & réinitialisation)

- **Schéma** : entièrement piloté par **Liquibase** (85 fichiers de changelog, 96 changesets).
  Aucune commande à lancer : les migrations sont appliquées **à chaque démarrage du backend**,
  avant l'ouverture du port. `ddl-auto` est sur `none` — Hibernate ne crée jamais rien.
- **Données de démonstration** : générées au démarrage en profil `dev` (`app.seed.enabled`),
  de façon **idempotente** — un redémarrage ne duplique rien.
- **Persistance** : la base vit dans le volume Docker `postgres_data`. `docker compose down`
  la conserve, `docker compose down -v` la détruit.

```bash
# Repartir d'une base vierge (re-migrée et re-seedée au prochain démarrage)
docker compose down -v
docker compose up -d

# Inspecter la base
docker compose exec postgres psql -U postgres -d coachrun -c '\dt'
docker compose exec postgres psql -U postgres -d coachrun -c 'select count(*) from databasechangelog;'

# Sauvegarde / restauration ponctuelle
docker compose exec -T postgres pg_dump -U postgres coachrun > sauvegarde.sql
docker compose exec -T postgres psql -U postgres -d coachrun < sauvegarde.sql
```

> Un script de sauvegarde prêt à l'emploi pour l'exploitation existe par ailleurs :
> [`ops/backup-db.sh`](./ops/backup-db.sh) (cf. [`docs/OPERATIONS.md`](./docs/OPERATIONS.md)).

---

## Problèmes courants

| Symptôme | Cause | Solution |
|---|---|---|
| `Cannot connect to the Docker daemon` | Docker Desktop / le démon n'est pas démarré | démarrer Docker, puis `docker version` |
| `bind: address already in use` sur 4200, 8081 ou 5432 | un PostgreSQL ou un serveur local occupe déjà le port (5432 est le cas le plus fréquent) | `FRONTEND_PORT=4300 BACKEND_PORT=8082 POSTGRES_PORT=55432 docker compose up -d` |
| `dependency failed to start: container coachrun-backend is unhealthy` | le backend n'a pas démarré à temps, ou a échoué | `docker compose logs backend` — le premier lancement (migrations + seed) est le plus long ; le budget de santé est de 60 s de grâce puis 20 tentatives × 15 s |
| Le front s'affiche mais toutes les requêtes échouent | le backend n'est pas sain | `docker compose ps` puis `docker compose logs backend` |
| Page blanche ou ancienne version après un rebuild du front | le **service worker** de la PWA sert une version en cache | rechargement forcé (Ctrl/Cmd + Maj + R), ou onglet privé, ou DevTools → *Application* → *Service Workers* → *Unregister* |
| Le build du front échoue en `JavaScript heap out of memory` | mémoire allouée à Docker trop faible | augmenter la RAM de Docker Desktop (Settings → Resources), ~4 Go |
| `docker-compose: command not found` | v1 absente : le projet utilise le plugin v2 | utiliser `docker compose` (deux mots) |
| Erreur PostgreSQL au démarrage après un changement de version d'image | volume créé par une version majeure différente | `docker compose down -v` (⚠ perte des données locales) |
| Le build est très lent / retélécharge tout | cache Docker invalidé | ne pas utiliser `--no-cache` sans raison ; `docker compose build` réutilise les couches |

---

## Développement local (sans Docker)

Utile pour le rechargement à chaud (`ng serve`) ou pour déboguer le backend dans un IDE.

**Prérequis** : **JDK 21**, **Node 20+** (npm 10+), et un **PostgreSQL** accessible.

### 1. PostgreSQL

Soit la base de la stack Docker, seule :

```bash
docker compose up -d postgres      # expose localhost:5432, base coachrun
```

Soit un PostgreSQL installé sur le poste, avec une base `coachrun` accessible par
`postgres` / `postgres` (valeurs par défaut de `application.yml`, surchargées par
`JDBC_DATABASE_URL`, `PGUSER`, `PGPASSWORD`).

### 2. Backend (port 8080)

```bash
cd back
./mvnw spring-boot:run             # profil dev par défaut → http://localhost:8080/api
```

Vérification : `curl http://localhost:8080/api/public/ping` → `{"status":"ok","version":"0.2.0"}`.

Aucun `.env` n'est nécessaire en dev : les valeurs par défaut d'`application.yml` suffisent.
Pour personnaliser (Strava, Resend, Sentry…), copier `.env.example` et exporter les variables
voulues dans le shell qui lance l'application.

### 3. Frontend (port 4200)

```bash
cd front
npm install
npm start                          # ng serve + proxy /api → http://localhost:8080
```

`front/proxy.conf.json` renvoie `/api` vers le backend local : le front appelle donc **8080**,
pas 8081.

> ⚠️ **Les ports diffèrent du mode Docker** : l'API est sur **8080** en local et sur **8081**
> avec `docker compose` (le conteneur écoute toujours 8080 en interne).

### Autres commandes front

```bash
npm run build                      # build de production (celui de Vercel)
npm run build:docker               # build de production sans DSN Sentry (celui de l'image Docker)
npm run start:pwa                  # build PWA + service worker, pour éprouver le push en local
npm test                           # tests Karma (Chrome headless)
```

---

## Structure du dépôt

```
.
├── back/                       # API Spring Boot (Java 21, Maven)
│   └── src/main/java/com/coachrun/
│       ├── controller/         # 47 contrôleurs REST
│       ├── service/            # 61 services métier
│       ├── engine/             # 12 moteurs de calcul (physiologie pure) + PaceUtil
│       ├── entity/             # entités JPA (héritent de BaseEntity)
│       ├── dto/                # request/ et response/ séparés
│       ├── repository/         # Spring Data JPA
│       ├── security/           # JWT, chiffrement, CORS, rate-limit, anti-IDOR
│       └── integration/        # Strava, Resend (clients HTTP)
│   └── src/main/resources/db/changelog/   # 85 changelogs Liquibase (96 changesets)
├── front/                      # App Angular (PWA)
│   └── src/app/
│       ├── core/               # services, models, guards, intercepteurs
│       └── features/           # 25 modules (athletes, strength, physio, messages, calendar…)
├── docs/                       # Cahier des charges, design, techno, audits, exploitation
│   ├── README.md               # index : quel document fait foi, et sur quoi
│   └── archive/                # historique non maintenu (CDC d'origine, blueprint, wireframes)
├── docker-compose.yml
├── .env.example
└── .github/workflows/ci.yml
```

**Multi-tenant** : chaque ressource est scopée par club via `@clubAccessValidator.hasAccess(...)`
(protection anti-IDOR systématique, couverte par des tests d'accès dédiés).

---

## Qualité, tests & CI

```bash
# Backend : build + tests + smoke test de démarrage
cd back && ./mvnw -B -ntp clean verify

# Frontend : build de production (typecheck AOT)
cd front && npm run build
```

- **~340 tests backend** (moteurs purs + intégration MockMvc), exécutés sur **H2 en mode PostgreSQL**,
  et **64 tests front** (Karma headless), tous joués en CI.
- **Smoke test de démarrage** en CI sur un **PostgreSQL réel** (migrations Liquibase appliquées) pour
  attraper les écarts H2↔PG.
- CI GitHub Actions (`.github/workflows/ci.yml`) : jobs back (`mvn verify` + smoke PG) et front (`npm ci` + build).

---

## Sécurité & RGPD

- **Chiffrement au repos** des données sensibles (mesures de santé : lactate, douleur… ; jetons OAuth)
  en **AES-256-GCM** avec IV aléatoire par valeur (`EncryptionService` + converters JPA).
- **JWT** stateless, TTL court, en-tête `Authorization: Bearer`.
- **Garde-fou au démarrage** (`StartupSecretsValidator`) : l'application **refuse de démarrer en prod**
  si `JWT_SECRET` ou `FIELD_ENCRYPTION_KEY` sont laissés à leurs valeurs par défaut.
- **En-têtes** : Content-Security-Policy, `frame-options: deny`, `object-src 'none'`.
- **CORS** restreint à une allowlist · **rate-limiting** par fenêtre fixe.
- **Anti-IDOR** : toute route club passe par `@clubAccessValidator` (privé / club / permissions).

> Exploitation (Sentry, sauvegardes, uptime) : [`docs/OPERATIONS.md`](./docs/OPERATIONS.md) et
> [`docs/BETA-LAUNCH-RUNBOOK.md`](./docs/BETA-LAUNCH-RUNBOOK.md). Ce qui reste à lever avant
> d'ouvrir : [`docs/PLAN-CONFORMITE-BETA-2026-08.md`](./docs/PLAN-CONFORMITE-BETA-2026-08.md).

---

## Variables d'environnement

> **La stack Docker n'a besoin d'aucune de ces variables** : `docker-compose.yml` fournit lui-même
> ses valeurs de développement. Cette section concerne le **déploiement** (Railway/Vercel) et le
> développement **sans** Docker.

Copier `.env.example` → `.env` (local) ou configurer dans Railway/Vercel. Clés principales :

| Variable | Rôle | Prod |
|---|---|---|
| `JDBC_DATABASE_URL`, `PGUSER`, `PGPASSWORD` | connexion PostgreSQL | requis |
| `JWT_SECRET` | secret de signature JWT (≥ 256 bits) | **requis** |
| `FIELD_ENCRYPTION_KEY` | clé de chiffrement (64 hex / 32 octets) | **requis** |
| `FRONTEND_URL`, `CORS_ORIGINS` | URL front + allowlist CORS | requis |
| `MAIL_ENABLED`, `RESEND_API_KEY`, `MAIL_FROM` | notifications e-mail | optionnel |
| `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` | notifications push | optionnel |
| `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `STRAVA_REDIRECT_URI` | intégration Strava | optionnel |
| `DEMO_RESET_ENABLED` | autorise la RAZ démo (jamais en prod) | défaut `false` |
| `SENTRY_DSN` | DSN Sentry **backend** (remontée d'erreurs) — no-op si vide | optionnel |
| `SENTRY_ENV` | environnement Sentry backend (`production` / `staging`…) | défaut `dev` |

### Monitoring (Sentry)
- **Backend** : `SENTRY_DSN` + `SENTRY_ENV` (variables d'environnement). Sentry est **inactif tant que `SENTRY_DSN` est vide**.
- **Frontend** : le DSN se renseigne dans `front/src/environments/environment.ts` (clé `sentryDsn`)
  au moment du build de prod (idéalement injecté via une variable CI), avec `appVersion` pour le suivi par release.
  L'image Docker, elle, est construite avec `npm run build:docker`
  (`front/src/environments/environment.docker.ts`) : même build de production, **sans DSN** — une
  stack locale n'a rien à écrire dans le projet Sentry des utilisateurs réels.

> L'intégration Strava se **désactive proprement** si les identifiants sont absents
> (l'UI affiche « non configuré » au lieu d'échouer). Tableau complet : [`docs/DEPLOIEMENT.md`](./docs/DEPLOIEMENT.md).
> **Mise en place pas-à-pas de Sentry, des sauvegardes BDD et du monitoring : [`docs/OPERATIONS.md`](./docs/OPERATIONS.md).**

---

## Déploiement

- **Backend + PostgreSQL → Railway** : service Docker depuis `/back` + plugin PostgreSQL.
- **Frontend → Vercel** : Root Directory = `front`, build Angular, rewrites SPA.

Procédure pas-à-pas (ordre de déploiement, variables, CORS, redirect URIs) :
[`docs/DEPLOIEMENT.md`](./docs/DEPLOIEMENT.md).

---

## Limites connues & pistes d'amélioration

Transparence sur ce qui reste à durcir (détail et priorisation dans
[`docs/PLAN-CONFORMITE-BETA-2026-08.md`](./docs/PLAN-CONFORMITE-BETA-2026-08.md) pour
l'exploitation, [`docs/AUDIT-BETA-OUVERTE-2026-08.md`](./docs/AUDIT-BETA-OUVERTE-2026-08.md) pour
le produit et les parcours) :

- **Tests sur PG réel** : les assertions tournent sur H2 (mode PostgreSQL) ; **Testcontainers**
  fermerait le risque H2↔PG (le CI ne fait qu'un smoke de démarrage sur PG).
- **Couverture front** : tests unitaires/e2e à étoffer (pas de Playwright/Cypress).
- **SSE mono-instance** : les émetteurs sont en mémoire → nécessite Redis pub/sub pour le multi-pod.
- **Jeton en query param** pour le flux SSE et le téléchargement des pièces jointes → à remplacer par
  des jetons courts signés.
- **Import Strava** par polling (webhook à venir) — le `state` OAuth est désormais signé (HMAC, TTL 10 min).
- **Pagination** à généraliser sur les listes non bornées (fil de messages, résultats…).
- **Pièces jointes en base** (`bytea`) → stockage objet (S3) à plus grande échelle.

---

## Documentation

> 📑 **[`docs/README.md`](./docs/README.md) est l'index de référence** : il dit quel document fait
> foi, lesquels sont des audits datés, et ce qui n'est conservé que pour l'historique.

| Document | Contenu |
|---|---|
| [`docs/Cahier-des-charges.md`](./docs/Cahier-des-charges.md) | périmètre fonctionnel (référentiel de complétude) |
| [`docs/Techno.md`](./docs/Techno.md) | référence technique |
| [`docs/Design.md`](./docs/Design.md) | design system (tokens, composants, états) |
| [`docs/Claude.md`](./docs/Claude.md) | conventions de code (IA & humains) |
| [`docs/DEPLOIEMENT.md`](./docs/DEPLOIEMENT.md) | déploiement Railway/Vercel + variables |
| [`docs/DEBUG.md`](./docs/DEBUG.md) | **mémo de débogage : points d'arrêt en Docker, IntelliJ et VS Code, journaux, correlationId** |
| [`docs/OPERATIONS.md`](./docs/OPERATIONS.md) | **exploitation : Sentry, sauvegardes BDD, Actuator, CI (pas-à-pas)** |
| [`docs/BETA-LAUNCH-RUNBOOK.md`](./docs/BETA-LAUNCH-RUNBOOK.md) | **runbook de mise en service : Resend, Sentry, uptime, backups (pas-à-pas)** |
| [`docs/PLAN-CONFORMITE-BETA-2026-08.md`](./docs/PLAN-CONFORMITE-BETA-2026-08.md) | **plan de mise en conformité** : vagues 0 à 3, check-lists légale/RGPD et opérationnelle, recommandation GO/NO-GO |
| [`docs/ANALYSE-CONCURRENTIELLE-NOLIO-2026-08.md`](./docs/ANALYSE-CONCURRENTIELLE-NOLIO-2026-08.md) | **analyse concurrentielle Nolio** : fonctionnalités, UX, UI, ergonomie, verdict, maturité, feuille de route |
| [`docs/AUDIT-FONCTIONNEL-2026-08.md`](./docs/AUDIT-FONCTIONNEL-2026-08.md) | audit métier : parcours coach/athlète sur un mésocycle réel (prescription, charge, alertes, blessure, force) |
| [`docs/AUDIT-BETA-OUVERTE-2026-08.md`](./docs/AUDIT-BETA-OUVERTE-2026-08.md) | audit de bêta ouverte : second passage (builds exécutés, consentement santé, autorisations club, plafonds SSE et e-mail) |
| [`docs/AUDIT-PRODUIT-WAHOU-2026-08.md`](./docs/AUDIT-PRODUIT-WAHOU-2026-08.md) | **audit produit / UX / métier** : les dix évolutions de la couche de décision, et la contrainte qui les gouverne — rien n'est appliqué sans validation humaine |
| [`docs/AUDIT-TECHNIQUE-2026-08.md`](./docs/AUDIT-TECHNIQUE-2026-08.md) | audit technique : chemin push, consentement santé, gestion d'erreurs |
| [`docs/AUDIT-PWA-COACH-2026-08.md`](./docs/AUDIT-PWA-COACH-2026-08.md) | audit PWA mobile côté coach (les trois vagues sont livrées) |
| [`docs/AUDIT-COACH-INDEPENDANT-2026-08.md`](./docs/AUDIT-COACH-INDEPENDANT-2026-08.md) | audit du coach indépendant : athlètes privés, multi-club |
| [`docs/PLAN-EVOLUTION-2026-08.md`](./docs/PLAN-EVOLUTION-2026-08.md) · [`docs/PLAN-PRODUIT-2026-08.md`](./docs/PLAN-PRODUIT-2026-08.md) | plans d'évolution : ce que le marché réclame, et ce que le modèle de domaine empêche |
| [`docs/DEMANDES-API-GARMIN-COROS.md`](./docs/DEMANDES-API-GARMIN-COROS.md) | dossiers d'accès aux API montres (Garmin fermé, COROS ouvert) |
| [`docs/archive/`](./docs/archive/) | historique non maintenu : blueprint UX (encore cité par des composants), wireframes, deux audits de juillet |

> **Aide utilisateur intégrée** : chaque espace dispose d'un **centre d'aide** adapté à son profil
> (athlète `/athlete/help`, coach `/app/aide`, admin `/admin/aide`), avec **recherche globale**
> (icône loupe dans chaque en-tête), **liens contextuels** (`<app-help-hint section="…">` ouvre la
> bonne rubrique depuis un écran) et **export PDF** d'un guide. Le contenu — éditable sans toucher au
> rendu — vit dans [`front/src/app/features/help/help-content.ts`](./front/src/app/features/help/help-content.ts).

---

## Conventions

- Entités héritant de `BaseEntity` · DTOs **Request/Response séparés** · scoping multi-tenant systématique.
- Composants Angular **standalone** + `inject()` · signals + OnPush · feedback via toasts.
- **Toute** modification de schéma passe par une migration Liquibase (jamais de `ddl-auto`).
- **Nommage anglais dans le code, libellés français dans l'UI.**

---

## Licence

_Privée — tous droits réservés (à définir)._
