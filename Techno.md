# Techno.md — Référence technique complète

> Référence technique extraite de *Mon Petit Atelier*. Sert de blueprint pour recréer un projet
> avec le même ADN technique. Sections _(hypothèse)_ = déductions issues du code observé.

---

## 1. Stack

### Backend
| Couche | Choix | Version |
|---|---|---|
| Framework | Spring Boot | 3.2.2 |
| Langage | Java | 21 |
| ORM | Spring Data JPA + Hibernate | — |
| Migrations | Liquibase (YAML) | — |
| Base de données | PostgreSQL | 14+ (16 en CI/Docker) |
| Auth | Spring Security + JWT (jjwt 0.12.3, HS512) + Magic Links | — |
| Mapping | MapStruct 1.5.5 + Lombok | — |
| PDF | iTextPDF 7.2.5 + OpenHtmlToPDF 1.0.10 | — |
| Email | Resend (via Spring Mail / API HTTP) | — |
| SMS | Twilio SDK | 10.1.0 |
| Push | nl.martijndwars web-push (VAPID) | — |
| Stockage objet | AWS SDK v2 S3 (R2/MinIO/S3 compatible) | 2.25.11 |
| Scheduling distribué | ShedLock | — |
| Doc API | Springdoc OpenAPI / Swagger | 2.3.0 |
| Monitoring | Sentry (sentry-spring-boot-starter-jakarta) | 7.8.0 |
| Build | Maven | — |

### Frontend
| Couche | Choix | Version |
|---|---|---|
| Framework | Angular (standalone components) | 17.3 |
| Langage | TypeScript | 5.4 |
| Réactivité | RxJS | 7.8 |
| PWA | @angular/service-worker | 17.3 |
| QR codes | angularx-qrcode | 17 |
| Monitoring | @sentry/angular | 8 |
| Build | Angular CLI | 17.3 |
| Tests | Karma + Jasmine | — |
| Icônes/images | sharp (génération build) | — |

### Services externes
Resend (email) · Twilio (SMS) · WebPush/VAPID (push) · S3-compatible (fichiers) · Sentry (erreurs front+back) · Vercel (hébergement front + Analytics/Speed Insights).

---

## 2. Architecture

### Organisation des dossiers
```
projet/
├── back/src/main/java/com/<org>/
│   ├── config/         # SecurityConfig, CorsConfig, AsyncConfig, ShedLockConfig, OpenApiConfig, DataSeeder…
│   ├── controller/     # REST controllers (~27), un par ressource
│   ├── service/        # Logique métier (~45 services)
│   ├── entity/         # Entités JPA (héritent de BaseEntity) + entity/enums/
│   ├── repository/     # Spring Data JPA
│   ├── dto/request/ + dto/response/   # DTOs séparés entrée/sortie
│   ├── security/       # JWT, filtres, validators, converters chiffrement, @RequiresModule
│   ├── scheduler/      # Tâches @Scheduled (ShedLock)
│   ├── exception/      # GlobalExceptionHandler + exceptions métier
│   └── util/
├── back/src/main/resources/
│   ├── application{,-dev,-prod}.yml
│   ├── db/changelog/changes/   # Migrations Liquibase NNN-*.yaml + master changelog
│   └── templates/      # Templates HTML PDF
└── front/src/app/
    ├── core/           # services/, models/, guards/, interceptors/
    ├── features/       # Modules fonctionnels (un dossier par domaine)
    ├── public/         # Pages publiques (landing, booking, légal, inscription)
    └── shared/         # components/, pipes/, directives/, services/, utils/
```

### Séparation des responsabilités
- **Controller** : routing, validation (`@Valid`), mapping DTO↔entité, autorisation (`@PreAuthorize`, `@RequiresModule`). Transactionnel en lecture par défaut.
- **Service** : logique métier, scoping tenant, transitions d'état, transactions d'écriture, orchestration des effets de bord.
- **Repository** : accès données (méthodes `findByIdAndGarageId`, JOIN FETCH pour éviter N+1).
- **DTO** : contrat d'API stable, découplé des entités.
- **Front service** : couche HTTP unique par ressource ; les composants ne connaissent jamais les URLs.

### Patterns utilisés
Multi-tenant scoping par parent · Machine à états (transition maps) · Notification trigger centralisé · Queue + retry (emails) · Dédup idempotente (réserve-puis-envoie, `REQUIRES_NEW`) · Optimistic locking (`@Version`) · Pessimistic lock pour séquences numérotées · Encrypted converter (AES-256-GCM) · Module gating (interceptor) · Soft-delete / archivage per-tenant · Retry transactionnel sur conflit de réservation (SERIALIZABLE + retry 40001).

### Gestion de l'état
- **Backend** : stateless (`SessionCreationPolicy.STATELESS`), état en DB.
- **Frontend** : **pas de store global**. Données chargées en `ngOnInit()` via services ; état local au composant ; brouillons en `localStorage` (auto-save). `BehaviorSubject` dans les services pour l'état partagé léger (ex. auth, modules). _(hypothèse pour auth/module services)_

### Gestion des données
- JPA/Hibernate, `open-in-view: false` (pas de lazy loading hors transaction → JOIN FETCH explicites).
- `ddl-auto: none` → **schéma piloté exclusivement par Liquibase**.
- Pagination serveur (`PageRequest`, params `page`/`size`).
- Champs sensibles chiffrés au repos ; recherche sur ces champs en Java post-déchiffrement.

### Gestion des erreurs
- **Back** : `@RestControllerAdvice` global → réponse JSON normalisée `{ status, message, timestamp, path }`. Mapping : 404 (NotFound), 400 (BadRequest/Validation avec `fieldErrors`), 409 (Conflict/DataIntegrity/OptimisticLock/IllegalState), 403 (AccessDenied/ModuleNotEnabled), 500 (avec `correlationId` loggué). Jamais de stacktrace exposée.
- **Front** : intercepteur HTTP global → toast selon le code (0 réseau, 401 logout+redirect, 403, 429, 400 message serveur, ≥500 générique). Liste `SILENT_PATTERNS` pour les écrans gérant leurs propres erreurs (login, booking…).

---

## 3. Backend

### APIs
- REST, base path `/api`, conventions ressources imbriquées `/garages/{garageId}/<resource>`.
- Routes publiques explicites : `/auth/**`, `/public/**`, Swagger, `/actuator/health|info`.
- `/admin/**` réservé au rôle ADMIN ; `/actuator/**` (hors health/info) ADMIN.
- Documentation auto Swagger UI (`/swagger-ui.html`).

### Base de données
- PostgreSQL, schéma `public`, ~23+ tables.
- Migrations Liquibase numérotées `NNN-description.yaml`, incluses dans `db.changelog-master.yaml`. Une migration par changement, jamais réutilisée, jamais éditée après merge.
- `BaseEntity` (`id` UUID `GenerationType.UUID`, `createdAt` `@CreationTimestamp`, `updatedAt` `@UpdateTimestamp`).
- Contraintes UNIQUE + index partiels per-tenant (ex. numéro de facture). Index explicites sur colonnes filtrées fréquemment.
- Smoke test CI : démarrage réel pour valider les migrations.

### Authentification
- **JWT** HS512, access token court (8h ici / recommandé 1h) + refresh token (7j) avec rotation (`POST /auth/refresh`).
- Transport dual : header `Authorization: Bearer` **et** cookie httpOnly (`withCredentials`).
- **Blacklist** de tokens (table + TTL, révocation au logout, nettoyage planifié).
- **Magic Links** : auth client sans mot de passe (token usage unique, typé, expirant).
- **BCrypt** pour les mots de passe staff. Validation des secrets au démarrage en prod (refus de démarrer si secret par défaut).

### Sécurité
- Spring Security stateless + `@EnableMethodSecurity`.
- `@PreAuthorize("@garageAccessValidator.hasAccess(authentication, #garageId)")` sur tout controller scopé → anti-IDOR.
- Headers : CSP, HSTS (1 an, preload), `X-Frame-Options: DENY`, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`.
- **Rate limiting** (`RateLimitingFilter`, fenêtre glissante 60s en mémoire) : auth 5/min, upload 10/min, booking 10/min, public 30/min.
- **Chiffrement au repos** AES-256-GCM (`EncryptedStringConverter`) : IBAN, BIC, VIN, immatriculation. Clé `FIELD_ENCRYPTION_KEY` (64 hex), fallback plaintext en dev.
- **Upload** : 5 Mo max, images uniquement, guard explicite.
- **Audit log** des actions sensibles (acteur, IP, décompte).
- **RGPD** : consentement granulaire (5 types + preuve IP/UA), anonymisation, export JSON (art. 20), droit à l'oubli, purge planifiée, suppression physique des fichiers.

### Jobs / Cron
- `@Scheduled` + **ShedLock** (verrou DB → safe en multi-instance).
- Exemples : rappels J-1 (08:00), relances entretien (10:30), purge RGPD (01:30), nettoyage réservations expirées (toutes les 10 min), traitement queue email (60s), nettoyage blacklist tokens (horaire).

### Intégrations
- **Email** : Resend, templates HTML en Java text blocks (⚠️ redémarrage requis après modif), queue + retry ×3, dead-letter (WARN). HTML des champs user échappé.
- **SMS** : Twilio, 3 scénarios transactionnels gardés par toggles tenant (pas par consentement marketing).
- **Push** : WebPush VAPID vers le staff.
- **Stockage** : abstraction `StorageService` → local (dev) ou S3 (prod) via `STORAGE_TYPE`.

---

## 4. Frontend

### Structure des composants
- **100 % standalone**, dépendances dans `imports[]`, injection via `inject()`.
- Convention par ressource : `xxx-list` / `xxx-detail` / `xxx-form`.
- 3 zones : `features/` (back-office authentifié), `public/` (landing, booking, légal), `shared/` (réutilisable).

### Routing
- `provideRouter(routes)`, **lazy `loadComponent` partout**.
- Guards fonctionnels (`CanActivateFn` via `inject()`) : `authGuard`, `adminGuard`, `garageOwnerGuard`, `mechanicGuard`, `clientGuard`, `publicOnlyGuard`.
- Routes imbriquées avec `children` + paramètres `:id` / `:id/edit`.

### Formulaires
- `ReactiveFormsModule` pour les formulaires complexes, `FormsModule`/`ngModel` pour les bindings simples.
- Validation : Validators Angular + classes d'état `.ng-invalid.ng-touched` + `.error-message`.
- Auto-save brouillon `localStorage` (debounce ~2s) + bannière de restauration.
- Sélecteurs réutilisables (`app-searchable-select`, `app-client-selector`, `app-vehicle-selector`, `app-line-items-editor`).

### Gestion des requêtes
- `provideHttpClient(withInterceptors([authInterceptor, errorInterceptor]))`.
- `authInterceptor` : `withCredentials` + header Bearer (compat), bypass `/public/`.
- `errorInterceptor` : toasts globaux par code HTTP + logout sur 401.
- Services : `@Injectable({ providedIn: 'root' })`, retournent des `Observable`, URL `environment.apiUrl`.

### Validation & i18n
- `LOCALE_ID = 'fr-FR'`, `registerLocaleData(localeFr)`.
- Pipes de mapping enum→français (ex. `StatusLabelPipe`).

### Performance
- Lazy loading par route (code splitting).
- Skeletons plutôt que spinners pour le perçu.
- Service worker (`registerWhenStable:30000`), cache PWA (`ngsw-config.json`).
- JOIN FETCH back + pagination → pas de sur-fetch.
- `takeUntil(destroy$)` pour éviter les fuites d'abonnement.

---

## 5. DevOps

### Déploiement
- **Front** : Vercel (`vercel.json` : rewrite SPA `/(.*) → /index.html`). Build `ng build --configuration=production`.
- **Back** : Docker (`Dockerfile` multi-stage présumé) + `docker-compose.yml` (postgres + back + front + nginx) pour le local.
- Profils Spring : `dev` (défaut, seed de démo) / `prod` (secrets obligatoires, reset DB bloqué).

### CI/CD
- GitHub Actions (`.github/workflows/ci.yml`) sur push/PR (`main`, `master`, `claude/**`), concurrency cancel-in-progress.
- **Job backend** : `mvn -B -ntp clean verify` + **boot smoke test** (démarrage réel sur Postgres éphémère → valide les migrations Liquibase via `/actuator/health`).
- **Job frontend** : `npm ci` + `npm run build` (typecheck AOT).
- Workflow `backup-db.yml` (sauvegarde DB planifiée).

### Variables d'environnement
- `.env.example` exhaustif et commenté ([PROD-REQUIS] / [DÉFAUT] / [OPTIONNEL]).
- Clés critiques : `JWT_SECRET`, `FIELD_ENCRYPTION_KEY` (64 hex), `DATABASE_URL`/`JDBC_DATABASE_URL`, `FRONTEND_URL`, `CORS_ORIGINS`, `RESEND_API_KEY`, `VAPID_*`, `TWILIO_*`, `S3_*`, `SENTRY_DSN`, `STORAGE_TYPE`.
- En prod, l'app **refuse de démarrer** si `JWT_SECRET`/VAPID restent par défaut.

### Monitoring & logging
- **Sentry** front + back (traces échantillonnées 10–20%, `send-default-pii: false` pour RGPD, niveau ≥WARN).
- **Actuator** : `health`, `info`, `metrics`, `loggers` (health/info publics, reste ADMIN).
- **Logs** : SLF4J/Logback (`@Slf4j`), tags structurés (`[NOT_FOUND]`, `[VALIDATION_FAILED]`, `correlationId` sur 500). Jamais de secret/PII.
- Vercel Analytics + Speed Insights côté front.

---

## 6. Recommandations

### À conserver absolument
- ✅ **Liquibase + smoke test CI** : garantit des migrations qui démarrent vraiment.
- ✅ **Multi-tenant scoping systématique** (`@PreAuthorize` + `findByIdAndGarageId`) → anti-IDOR robuste.
- ✅ **DTO Request/Response séparés** + `@JsonIgnoreProperties(ignoreUnknown = true)`.
- ✅ **`BaseEntity` + UUID + timestamps auto**.
- ✅ **Design tokens CSS** + design system documenté (cf. `Design.md`).
- ✅ **Intercepteurs HTTP** (auth + erreurs centralisées) + toasts globaux.
- ✅ **Standalone components + lazy routing** partout.
- ✅ **Notification trigger centralisé** + queue/retry + dédup idempotente.
- ✅ **Sécurité by default** : headers, rate limiting, chiffrement au repos, validation secrets au boot.
- ✅ **RGPD-by-design** (consentement, export, anonymisation, audit).
- ✅ **`.env.example` exhaustif** + abstraction stockage local/S3.

### À améliorer (pistes)
- ⚠️ **Couverture de tests faible** _(hypothèse : seuls Karma/Jasmine présents, pas de tests visibles)_ → ajouter tests unitaires service + tests d'intégration controller (Testcontainers), tests front sur les services et guards.
- ⚠️ **Templates email en text blocks Java** (redémarrage requis) → externaliser en fichiers (Thymeleaf/ressources) pour hot-reload et lisibilité.
- ⚠️ **Rate limiting en mémoire** → ne tient pas en multi-instance ; passer sur Redis/bucket distribué.
- ⚠️ **JWT 8h** ici → réduire à 1h access + refresh (déjà câblé) pour limiter la fenêtre de compromission.
- ⚠️ **Migration vers Angular Signals** _(hypothèse)_ pour l'état local, et `ChangeDetectionStrategy.OnPush` pour les perfs.
- ⚠️ **Versioning d'API** (`/api/v1`) absent → l'introduire tôt pour les évolutions de contrat.
- ⚠️ **Mapping manuel DTO↔entité** dans certains controllers alors que MapStruct est présent → homogénéiser.

### Choix à reproduire dans les futurs projets
1. Spring Boot 3 / Java 21 + Angular 17 standalone + PostgreSQL + Liquibase.
2. Architecture en couches stricte (controller / service / repository / dto).
3. Sécurité stateless JWT (header + cookie) + scoping tenant + headers durcis dès le départ.
4. Design system tokenisé + intercepteurs + feedback toast unifié.
5. CI build+migrations+typecheck, déploiement Vercel (front) / Docker (back).
6. RGPD et conformité traités comme des fonctionnalités de premier ordre.
7. `.env.example` documenté + profils dev/prod avec garde-fous au démarrage.

---

*Blueprint technique dérivé de Mon Petit Atelier — adapter les intégrations (Resend/Twilio/S3) et le domaine métier au nouveau projet.*
