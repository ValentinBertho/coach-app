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
- **Profil physiologique** par athlète : performances de référence → **VDOT** + allures, seuils
  **LT1/LT2** (tests lactate), **domaines d'intensité** (1/2/3), FC de seuil.
- **Bibliothèque de séances** + catégories, **éditeur de structure unique** (échauffement / corps /
  retour au calme) en **fourchettes** (% LT1/LT2/VC/allures) avec **calculateur live** par athlète.
  Saisie simplifiée (bascule Distance/Durée, durée en minutes, blocs pré-remplis) ; **éducatifs de
  course** (gammes technique/amplitude) attachables aux blocs. Affichage **cartes compactes ↔ liste
  dense** avec recherche et filtre par type.
- **Calendrier** multi-types (course / force / test / objectif / indispo) avec drag & drop et
  snapshot figé de la prescription. **Bibliothèque latérale repliable** (semaine pleine largeur) ;
  les actions d'écriture (planifier, dupliquer la semaine, mésocycle) sont **désactivées sur un
  athlète en lecture seule** (cohérent avec la permission `write`).
- **Dashboard coach** : tableaux Route/Trail, **pastilles de forme** (fatigue + douleur, jamais RPE),
  portée mes athlètes / privés / club.
- **Portail athlète** (PWA, offline-friendly) : séance du jour **avec cibles en fourchettes**
  (allure/FC/RPE) et éducatifs liés, retour (RPE / fatigue / douleur / commentaire), **déplacement**
  de séance (jamais de modification/suppression), **mes activités** (saisie manuelle + import GPX/TCX),
  **mes objectifs** (CRUD A/B/C) et **connexion de sa montre** (Strava) directement côté athlète.

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
  fichier **GPX/TCX** et **saisie manuelle** d'une sortie par l'athlète. Garmin / COROS : prévus
  (enum présent), non implémentés.
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
| **Backend** | Spring Boot 3.2.5, Java 21, API REST (~236 endpoints), Springdoc/OpenAPI |
| **Base de données** | PostgreSQL 16 · **Liquibase** (42 migrations versionnées) |
| **Auth** | JWT (access tokens) + liens magiques d'invitation athlète · `@PreAuthorize` multi-tenant |
| **Sécurité** | AES-256-GCM (données santé + jetons OAuth chiffrés au repos), CSP, CORS allowlist, rate-limiting |
| **Intégrations** | Strava (OAuth), import GPX/FIT, e-mail Resend, Web Push (VAPID), export PDF (OpenPDF) |
| **Temps réel** | Server-Sent Events (messagerie) |
| **CI/CD** | GitHub Actions · Docker · Railway (back + DB) · Vercel (front) |

---

## Démarrage rapide (Docker)

Pré-requis : **Docker** (avec le démon démarré).

```bash
git clone <url-du-repo> && cd coach-app
docker compose up --build
```

| Service | URL |
|---|---|
| Frontend | http://localhost:4200 |
| API | http://localhost:8081/api |
| Swagger UI | http://localhost:8081/api/swagger-ui.html |
| Health | http://localhost:8081/api/actuator/health |
| PostgreSQL | localhost:5432 (`postgres` / `postgres`) |

En profil `dev`, un **jeu de données de démonstration complet** est chargé automatiquement au démarrage.

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
> et force structurées, cycles, tests 1RM, charge, objectifs et indisponibilités. Détails et procédure
> de réinitialisation dans [`docs/DEMO.md`](./docs/DEMO.md).

---

## Développement local (sans Docker)

### Backend (port 8080)
```bash
cd back
# PostgreSQL local requis (ou : docker compose up postgres)
./mvnw spring-boot:run          # profil dev, http://localhost:8080/api
```

### Frontend (port 4200)
```bash
cd front
npm install
npm start                       # proxy vers http://localhost:8080/api
```

> ⚠️ Les ports diffèrent du mode Docker : **8080** en dev local, **8081** exposé par `docker-compose`.

---

## Structure du dépôt

```
.
├── back/                       # API Spring Boot (Java 21, Maven)
│   └── src/main/java/com/coachrun/
│       ├── controller/         # 41 contrôleurs REST
│       ├── service/            # 47 services métier
│       ├── engine/             # 11 moteurs de calcul (physiologie pure) + PaceUtil
│       ├── entity/             # entités JPA (héritent de BaseEntity)
│       ├── dto/                # request/ et response/ séparés
│       ├── repository/         # Spring Data JPA
│       ├── security/           # JWT, chiffrement, CORS, rate-limit, anti-IDOR
│       └── integration/        # Strava, Resend (clients HTTP)
│   └── src/main/resources/db/changelog/   # 42 migrations Liquibase
├── front/                      # App Angular (PWA)
│   └── src/app/
│       ├── core/               # services, models, guards, intercepteurs
│       └── features/           # 21 modules (athletes, strength, physio, messages, calendar…)
├── docs/                       # Cahier des charges, design, techno, audit, déploiement
│   └── Darilab/                # CDC DARI Lab, architecture, plan d'implémentation, wireframes
├── docker-compose.yml
├── .env.example
└── .github/workflows/ci.yml
```

**Multi-tenant** : chaque ressource est scopée par club via `@clubAccessValidator.hasAccess(...)`
(protection anti-IDOR systématique, couverte par des tests d'accès dédiés).

---

## Qualité, tests & CI

```bash
# Backend : build + 151 tests + smoke test de démarrage
cd back && ./mvnw -B -ntp clean verify

# Frontend : build de production (typecheck AOT)
cd front && npm run build
```

- **151 tests backend** (moteurs purs + intégration MockMvc), exécutés sur **H2 en mode PostgreSQL**.
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

> Voir l'audit de préparation à la bêta (sécurité, scalabilité, plan d'action) dans
> [`docs/AUDIT-BETA-2026-06.md`](./docs/AUDIT-BETA-2026-06.md).

---

## Variables d'environnement

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
[`docs/AUDIT-BETA-2026-06.md`](./docs/AUDIT-BETA-2026-06.md)) :

- **Tests sur PG réel** : les assertions tournent sur H2 (mode PostgreSQL) ; **Testcontainers**
  fermerait le risque H2↔PG (le CI ne fait qu'un smoke de démarrage sur PG).
- **Couverture front** : tests unitaires/e2e à étoffer (pas de Playwright/Cypress).
- **SSE mono-instance** : les émetteurs sont en mémoire → nécessite Redis pub/sub pour le multi-pod.
- **Jeton en query param** pour le flux SSE et le téléchargement des pièces jointes → à remplacer par
  des jetons courts signés.
- **`state` OAuth Strava** à signer (nonce anti-CSRF) ; **import Strava** par polling (webhook à venir).
- **Pagination** à généraliser sur les listes non bornées (fil de messages, résultats…).
- **Pièces jointes en base** (`bytea`) → stockage objet (S3) à plus grande échelle.

---

## Documentation

| Document | Contenu |
|---|---|
| [`docs/Cahier-des-charges.md`](./docs/Cahier-des-charges.md) | périmètre fonctionnel |
| [`docs/Darilab/PLAN-IMPLEMENTATION.md`](./docs/Darilab/PLAN-IMPLEMENTATION.md) | plan d'implémentation (décisions, sprints, migrations) |
| [`docs/Techno.md`](./docs/Techno.md) | référence technique |
| [`docs/Design.md`](./docs/Design.md) | design system (tokens, charte) |
| [`docs/DEMO.md`](./docs/DEMO.md) | comptes & procédure de démonstration |
| [`docs/DEPLOIEMENT.md`](./docs/DEPLOIEMENT.md) | déploiement Railway/Vercel + variables |
| [`docs/OPERATIONS.md`](./docs/OPERATIONS.md) | **exploitation : Sentry, sauvegardes BDD, Actuator, CI (pas-à-pas)** |
| [`docs/ATHLETE-ROADMAP.md`](./docs/ATHLETE-ROADMAP.md) | audit des données visibles par l'athlète + plan d'évolution |
| [`docs/AUDIT-BETA-2026-06.md`](./docs/AUDIT-BETA-2026-06.md) | audit de préparation à la bêta, dette & plan d'action priorisé |
| [`docs/Claude.md`](./docs/Claude.md) | conventions de code (IA & humains) |

---

## Conventions

- Entités héritant de `BaseEntity` · DTOs **Request/Response séparés** · scoping multi-tenant systématique.
- Composants Angular **standalone** + `inject()` · signals + OnPush · feedback via toasts.
- **Toute** modification de schéma passe par une migration Liquibase (jamais de `ddl-auto`).
- **Nommage anglais dans le code, libellés français dans l'UI.**

---

## Licence

_Privée — tous droits réservés (à définir)._
