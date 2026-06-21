# Techno.md — Référence technique (Plateforme de coaching course à pied)

> Blueprint technique pour un SaaS de coaching running (alternative Nolio). Reprend l'ADN technique
> éprouvé du SaaS source (Spring Boot 3 / Angular 17 / PostgreSQL / Liquibase) et l'adapte au métier.
> Sections _(hypothèse)_ = recommandations à valider.

---

## 1. Stack

### Backend (inchangé — socle robuste)
| Couche | Choix | Version |
|---|---|---|
| Framework | Spring Boot | 3.2.x |
| Langage | Java | 21 |
| ORM | Spring Data JPA + Hibernate | — |
| Migrations | Liquibase (YAML) | — |
| Base de données | PostgreSQL | 14+ (16 conseillé) |
| Auth | Spring Security + JWT (jjwt, HS512) + Magic Links (athlètes) | — |
| Mapping | MapStruct + Lombok | — |
| Email | Resend (ou SMTP) | — |
| Push | WebPush VAPID | — |
| SMS _(optionnel)_ | Twilio | — |
| Stockage objet | AWS SDK v2 S3 (R2/MinIO/S3) — fichiers FIT/GPX, photos | — |
| Scheduling distribué | ShedLock | — |
| HTTP client externe | `RestClient` / `WebClient` (Strava, Garmin, Coros) | — |
| Parsing activités | lib FIT (Garmin FIT SDK) + parser GPX/TCX _(hypothèse)_ | — |
| Doc API | Springdoc OpenAPI / Swagger | — |
| Monitoring | Sentry | — |
| Build | Maven | — |

### Frontend
| Couche | Choix | Version |
|---|---|---|
| Framework | Angular (standalone) | 17+ |
| Langage | TypeScript | 5.4+ |
| Réactivité | RxJS | 7.8 |
| PWA | @angular/service-worker | — |
| Graphes | **à ajouter** : ngx-charts / Chart.js / D3 (charge, zones, allures) _(hypothèse)_ | — |
| Cartes _(optionnel)_ | Leaflet / MapLibre (tracé GPS) _(hypothèse)_ | — |
| Monitoring | @sentry/angular | — |
| Build/Tests | Angular CLI · Karma/Jasmine | — |

### Services externes
**Strava API**, **Garmin Connect / Health API**, **Coros / Polar Flow** (OAuth) · Resend (email) ·
WebPush (push) · S3-compatible (FIT/GPX, médias) · Sentry · Vercel (front) · **Stripe** _(hypothèse, facturation coaching privé)_.

---

## 2. Architecture

### Organisation des dossiers (identique au socle)
```
back/src/main/java/com/<org>/
  ├── config/        # SecurityConfig, CorsConfig, ShedLockConfig, OAuthClientsConfig…
  ├── controller/    # REST, une ressource par controller
  ├── service/       # Métier : plans, séances, sync, charge, notifications
  ├── entity/ + entity/enums/
  ├── repository/
  ├── dto/request/ + dto/response/
  ├── security/      # JWT, validators (clubAccessValidator), @RequiresModule, EncryptedConverter
  ├── integration/   # Clients Strava/Garmin/Coros + parsers FIT/GPX  (hypothèse, dossier dédié)
  ├── scheduler/     # Sync, calcul de charge, rappels (ShedLock)
  └── exception/
front/src/app/
  ├── core/          # services/, models/, guards/, interceptors/
  ├── features/      # coach: athletes, plans, calendar, workouts, library, analytics, messages, billing, settings
  ├── athlete/       # app athlète (PWA) : today, calendar, activities, wellness, messages, profile
  ├── public/        # landing, invitation athlète, légal, oauth-callback
  └── shared/        # components/, pipes/, directives/
```

### Séparation des responsabilités
Controller (routing, validation, autorisation, mapping DTO) → Service (métier, scoping tenant, transitions d'état, orchestration sync/notifications) → Repository (`findByIdAndClubId`, JOIN FETCH). DTOs = contrat d'API découplé des entités.

### Patterns
Multi-tenant scoping (`clubId`) · Machine à états (Workout/Plan) · Notification trigger centralisé · Sync idempotente (dédup par `externalId`) · Queue + retry (emails, imports) · Optimistic locking (`@Version`) · Encrypted converter (données de santé) · Module gating · Soft-archive per-tenant.

### Gestion de l'état
- **Back** : stateless, état en DB.
- **Front** : pas de store global ; données chargées en `ngOnInit()` via services ; brouillons (éditeur de séance/plan) en `localStorage` ; `BehaviorSubject` pour auth/modules/athlète courant _(hypothèse)_.

### Gestion des données
- `open-in-view: false`, `ddl-auto: none` → **schéma piloté par Liquibase uniquement**.
- Pagination serveur (`page`/`size`) sur listes (athlètes, activités).
- Champs de santé chiffrés au repos ; recherche post-déchiffrement en Java si nécessaire.
- **Séries temporelles d'activité** (samples FC/allure/altitude par seconde) : stocker le fichier brut (FIT/GPX) sur S3 + un résumé agrégé en DB (laps, moyennes, temps par zone). Ne pas exploser chaque sample en lignes SQL _(hypothèse perf)_.

### Gestion des erreurs
- **Back** : `@RestControllerAdvice` global → JSON normalisé `{status,message,timestamp,path}` ; 404/400(+`fieldErrors`)/409/403/500(+`correlationId`). Erreurs d'intégration externe (Strava down, token expiré) → mappées proprement (502/409) sans exposer le détail.
- **Front** : intercepteur HTTP global → toasts par code ; `SILENT_PATTERNS` pour login/oauth-callback.

---

## 3. Backend

### APIs (exemples de routes)
- `/auth/**`, `/public/**`, Swagger, `/actuator/health|info` → publics.
- `/clubs/{clubId}/athletes/{athleteId}/workouts` (CRUD séances)
- `/clubs/{clubId}/athletes/{athleteId}/activities` (réalisé, import)
- `/clubs/{clubId}/plans` · `/clubs/{clubId}/workout-templates`
- `/clubs/{clubId}/athletes/{athleteId}/wellness`
- `/clubs/{clubId}/athletes/{athleteId}/load` (charge calculée)
- `/clubs/{clubId}/messages`
- `/integrations/strava/connect` · `/integrations/strava/callback` · `/integrations/strava/webhook`
- `/admin/**` → `PLATFORM_ADMIN`.
- Scoping `@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")` + `@RequiresModule`.

### Base de données
- PostgreSQL, migrations Liquibase `NNN-*.yaml` (une par changement, incluses au master).
- `BaseEntity` (UUID, timestamps auto).
- Contraintes clés : **UNIQUE `(athlete_id, source, external_id)`** sur `activities` (dédup sync), index sur `(athlete_id, date)` pour calendrier/charge, index `(club_id, status)`.
- Smoke test CI : démarrage réel pour valider les migrations.

### Authentification
- **JWT** (access court + refresh avec rotation), transport header + cookie httpOnly, blacklist au logout.
- **Magic Links** pour les athlètes (onboarding sans mot de passe — invitation par le coach).
- **OAuth2** côté intégrations (Strava/Garmin) : flow Authorization Code, **tokens chiffrés au repos**, refresh automatique avant expiration (job planifié).
- BCrypt pour les comptes coach/admin ; validation des secrets au démarrage prod.

### Sécurité (+ spécificités santé)
- Stateless + `@EnableMethodSecurity` + scoping tenant (anti-IDOR : un coach ne voit que ses athlètes / son club).
- Headers durcis (CSP, HSTS, X-Frame-Options, Referrer-Policy, Permissions-Policy).
- **Rate limiting** : auth, import, webhook, public.
- **Chiffrement AES-256-GCM** : **données de santé** (FC repos, HRV, poids, pathologies, notes médicales) + **tokens OAuth**. Clé `FIELD_ENCRYPTION_KEY` (64 hex).
- **RGPD renforcé (données de santé = catégorie particulière, art. 9)** : consentement explicite et séparé pour la collecte physiologique et la connexion d'appareils ; export, droit à l'oubli (suppression activités + révocation OAuth + purge fichiers S3) ; minimisation.
- Webhooks Strava/Garmin : **vérification de signature** + validation du challenge.

### Jobs / Cron (ShedLock)
- **Sync activités** : polling périodique + traitement des webhooks (import → dédup → rapprochement prévu/réalisé).
- **Refresh tokens OAuth** avant expiration.
- **Calcul de charge** (CTL/ATL/forme) quotidien par athlète.
- **Rappels** : séance du lendemain, objectif J-7, athlète sans séance loggée depuis X jours.
- **Purge RGPD** planifiée ; nettoyage blacklist tokens ; traitement queue email.

### Intégrations
- **Strava** : OAuth + webhooks (`activity create`), récupération des détails + streams ; respect des **rate limits** (backoff).
- **Garmin / Coros / Polar** : OAuth/Health API ou import fichier.
- **Fichiers** : upload **FIT/GPX/TCX** manuel → parser → `Activity` + résumé (laps, temps par zone). Brut stocké S3.
- **Email/Push** : `NotificationTriggerService` centralisé, queue + retry, idempotence, HTML échappé.
- **Paiement** _(hypothèse)_ : Stripe (abonnements coaching privé) — webhooks signés, statut `Subscription`.

---

## 4. Frontend

### Structure des composants
- 100% standalone, `inject()`, lazy routing partout.
- **Deux espaces** : `features/` (coach) et `athlete/` (PWA athlète), guards par rôle (`coachGuard`, `athleteGuard`, `adminGuard`, `clubOwnerGuard`).
- Convention par ressource : `xxx-list` / `xxx-detail` / `xxx-form` (+ éditeurs spécialisés : `workout-editor`, `plan-builder`).

### Routing
- `provideRouter(routes)`, `loadComponent` lazy, `children` + `:id`/`:id/edit`.
- Route `oauth-callback` publique pour le retour Strava/Garmin.

### Formulaires & validation
- `ReactiveFormsModule` pour l'éditeur de séance structurée (FormArray d'étapes) et le plan builder.
- `FormsModule`/`ngModel` pour les saisies rapides (RPE, wellness).
- Auto-save brouillon `localStorage` (debounce) + bannière de restauration.

### Gestion des requêtes
- `provideHttpClient(withInterceptors([authInterceptor, errorInterceptor]))`.
- `authInterceptor` : `withCredentials` + Bearer, bypass `/public/`.
- `errorInterceptor` : toasts par code + logout 401.
- Services `@Injectable({ providedIn: 'root' })`, `Observable`, base `environment.apiUrl`.

### Performance
- Lazy loading par route (l'app athlète et l'app coach sont des bundles séparés).
- Skeletons plutôt que spinners.
- Service worker / cache PWA ; consultation offline de la séance du jour _(hypothèse)_.
- Graphes : agréger côté back (temps par zone, charge) → le front reçoit des données prêtes à tracer, pas des milliers de samples bruts.
- `takeUntil(destroy$)` anti-fuite.

---

## 5. DevOps

### Déploiement
- **Front** : Vercel (rewrite SPA `/(.*) → /index.html`), build prod AOT.
- **Back** : Docker + `docker-compose` (postgres + back + front) en local.
- Profils Spring `dev` (seed démo) / `prod` (secrets obligatoires, garde-fous).

### CI/CD
- GitHub Actions : job backend (`mvn -B -ntp clean verify` + **boot smoke test** Postgres éphémère → valide Liquibase) + job frontend (`npm ci` + `npm run build`). Concurrency cancel-in-progress. Sauvegarde DB planifiée.

### Variables d'environnement (`.env.example` exhaustif)
`JWT_SECRET`, `FIELD_ENCRYPTION_KEY` (64 hex), `JDBC_DATABASE_URL`/`PGUSER`/`PGPASSWORD`, `FRONTEND_URL`, `CORS_ORIGINS`,
`RESEND_API_KEY`/`MAIL_FROM`, `VAPID_*`, `S3_*`, `STORAGE_TYPE`, `SENTRY_DSN`,
**`STRAVA_CLIENT_ID`/`STRAVA_CLIENT_SECRET`/`STRAVA_WEBHOOK_VERIFY_TOKEN`**, `GARMIN_*`, `COROS_*`, **`STRIPE_*`** _(hypothèse)_.
En prod, l'app refuse de démarrer si `JWT_SECRET`/VAPID restent par défaut.

### Monitoring & logging
- Sentry front+back (`send-default-pii: false` — **crucial vu les données de santé**, traces échantillonnées).
- Actuator `health/info` publics, reste ADMIN.
- Logs SLF4J tagués + `correlationId` sur 500. **Jamais de donnée de santé / token / PII dans les logs.**

---

## 6. Recommandations

### À conserver absolument (depuis le socle)
- ✅ Liquibase + smoke test CI · multi-tenant scoping (`@PreAuthorize` + `findByIdAndClubId`) · DTO Request/Response séparés · `BaseEntity` UUID · design tokens · intercepteurs HTTP + toasts · standalone + lazy routing · notification trigger + queue/retry + dédup · sécurité by default (headers, rate-limit, chiffrement, validation secrets) · `.env.example` exhaustif · abstraction stockage local/S3.

### Spécifique coaching — à soigner dès le départ
- ✅ **Dédup d'import** par `(athlete_id, source, external_id)` UNIQUE — la fiabilité des stats en dépend.
- ✅ **Chiffrement des données de santé + tokens OAuth**, consentement RGPD art. 9 séparé.
- ✅ **Stockage hybride** : fichier brut FIT/GPX sur S3 + résumé agrégé en DB (pas de samples bruts en SQL).
- ✅ **Calcul de charge** isolé dans un service testable (CTL/ATL/forme) avec formules documentées.
- ✅ **Gestion des rate limits** Strava (backoff + file d'import asynchrone).
- ✅ **App athlète et app coach séparées** (bundles + guards) — UX et perfs distinctes.

### À améliorer / décisions à prendre
- ⚠️ **Tests** : couvrir le calcul de charge, le rapprochement prévu/réalisé et les parsers FIT/GPX (tests unitaires + Testcontainers).
- ⚠️ **Choix lib graphes** (ngx-charts vs Chart.js vs D3) — trancher tôt, c'est structurant.
- ⚠️ **Temps réel** : messagerie/notifs en SSE/WebSocket si besoin d'instantané (sinon push + polling).
- ⚠️ **Modèle « coach solo sans club »** : décider si club implicite (1 club = 1 coach) ou entité `Coach` autonome — impacte tout le scoping. Recommandation : **club implicite** (uniformise le multi-tenant).
- ⚠️ **Externaliser les templates email** en fichiers (vs text blocks Java) pour hot-reload.
- ⚠️ **Versioning d'API** (`/api/v1`) dès le départ.
- ⚠️ **Angular Signals + OnPush** pour les vues data-lourdes (calendrier, graphes).

### Choix à reproduire dans les futurs projets
1. Spring Boot 3 / Java 21 + Angular 17 standalone + PostgreSQL + Liquibase.
2. Architecture en couches stricte + scoping tenant + sécurité durcie dès le départ.
3. Design system tokenisé + intercepteurs + feedback toast unifié + PWA mobile-first.
4. Intégrations externes isolées dans un dossier `integration/` avec clients dédiés et dédup idempotente.
5. RGPD/données de santé traitées comme une fonctionnalité de premier ordre.
6. CI build + migrations + typecheck ; Vercel (front) / Docker (back) ; `.env.example` documenté.

---

*Blueprint technique coaching running — générique et réutilisable. Activer/désactiver les modules
(`INTEGRATIONS`, `BILLING`, `WELLNESS`…) selon l'offre du coach/club.*
