# Audit de préparation à une **bêta ouverte publique** — DARI Lab Training

> Audit indépendant réalisé le **2026-07-17**.
> **Question unique traitée** : *qu'est-ce qui manque aujourd'hui pour que cette application soit
> réellement prête pour une **bêta ouverte publique** (self-service, plusieurs centaines/milliers
> d'utilisateurs) dans des conditions professionnelles ?*
>
> Méthode : lecture intégrale de la documentation (`README.md`, `docs/**`, CDC DARI Lab,
> `PLAN-IMPLEMENTATION`, `ux-redesign-blueprint`, `OPERATIONS`, `AUDIT-BETA-2026-06`), puis revue
> du code réel (front Angular 17 / back Spring Boot 3), config CI/CD, migrations, sécurité.
> **Les constats du code priment sur la documentation lorsqu'ils divergent** — et plusieurs
> divergences ont été trouvées (voir §8). Chaque conclusion est sourcée par un fichier.
>
> ⚠️ Cet audit **complète et corrige** `docs/AUDIT-BETA-2026-06.md`, qui concluait « GO pour une bêta
> **fermée** et accompagnée ». Le présent document répond à une exigence différente et plus haute :
> la **bêta ouverte publique**. Le verdict est donc plus sévère, ce qui est normal.

---

## 1. Résumé exécutif

DARI Lab Training est un produit **étonnamment mature pour son stade** : cœur métier physiologique
sérieux et testé (12 moteurs de calcul, 151 tests back verts), architecture multi-tenant propre,
portail athlète mobile offline-first soigné, ~236 endpoints, 42 migrations Liquibase, CI back+front.
Ce n'est **pas** un prototype.

**Mais il n'est pas prêt pour une bêta *ouverte publique*.** Un obstacle **bloquant et invisible à la
démo** ruine l'expérience en usage réel : **le rafraîchissement de session (refresh token) n'est pas
branché côté front** — l'utilisateur est **déconnecté de force au bout de ~15 minutes** (§4, §9). À
cela s'ajoutent : une **couverture de tests front quasi nulle** (2 specs, inchangé depuis juin),
l'**absence de toute base légale** (aucune politique de confidentialité / CGU / mentions légales alors
qu'on collecte des **données de santé RGPD art. 9**), une **protection anti-abus insuffisante** pour du
self-service (pas de captcha, `/password-reset` non rate-limité), l'**absence d'analytics produit** (on
ne pourra pas mesurer les KPI de la bêta), et une **infra mono-instance** non compatible avec la montée
en charge (schedulers sans verrou, SSE en mémoire).

**Verdict : NO-GO pour une bêta ouverte publique en l'état.** Le produit est en revanche **très proche
d'un GO pour une bêta fermée encadrée**. L'écart vers l'ouverture publique tient à ~8 chantiers ciblés,
dont **un seul est un vrai bug bloquant** (le refresh), les autres étant de la config, du durcissement,
du légal et du test. Aucun n'est un chantier structurel.

**Niveau de préparation estimé pour une bêta *ouverte publique* : ~62 %** (détail §17).

---

## 2. État global du projet

| Axe | Note | Commentaire (sourcé) |
|---|---|---|
| Cœur métier (moteurs physio) | 🟢 | 12 moteurs purs testés (`back/.../engine/`), 151 tests back verts |
| Adéquation au CDC | 🟢 | Phases 1+2 du CDC livrées, + module force/physio avancé |
| Architecture back | 🟢 | 41 contrôleurs, 49 services, multi-tenant anti-IDOR, `open-in-view: false` |
| Portail athlète mobile | 🟢 | Offline-first réel, états gérés, double séance/jour corrigée |
| **Cycle de session (auth)** | 🔴 | **Refresh non branché → déconnexion forcée ~15 min** (§4) |
| Tests **front** | 🔴 | **2 specs seulement** sur 84 composants (§13) |
| Conformité légale (bêta publique) | 🔴 | **Aucune** politique de confidentialité / CGU / mentions / licence (§10) |
| Protection anti-abus (self-service) | 🟠 | Pas de captcha, `/password-reset` non rate-limité (§9) |
| Observabilité | 🟠 | Sentry câblé mais optionnel ; **aucun analytics produit** (§11) |
| Scalabilité (montée en charge) | 🟠 | Schedulers sans ShedLock, SSE mono-instance, `bytea` (§8, §12) |
| Ops / prod-readiness | 🟠 | Dépend de config (mail, Sentry, backups) — docs présentes |

---

## 3. Forces (à préserver)

1. **Moteurs de calcul isolés et testés** (`back/src/main/java/com/coachrun/engine/`, 12 fichiers) :
   VDOT, seuils lactate, domaines d'intensité, charge (ACWR/ATL/CTL), 1RM, progression. C'est la vraie
   valeur du produit, et elle est solide (151 tests, `mvn verify` vert en CI).
2. **Sécurité applicative de bon niveau pour l'étape** :
   - Chiffrement au repos AES-256-GCM des données santé + jetons OAuth (`security/EncryptionService.java`,
     converters JPA).
   - Garde-fou au démarrage : `StartupSecretsValidator` refuse de booter en prod avec des secrets par défaut.
   - Anti-IDOR systématique multi-tenant (`ClubAccessValidator`, `AthleteAccessValidator`, `@PreAuthorize`).
   - En-têtes durcis (CSP, HSTS 1 an, frame-options deny, referrer-policy, permissions-policy) — `SecurityConfig.java:63-74`.
   - CORS en allowlist (`SecurityConfig.java:81-92`), rate-limit sur login/register (`RateLimitFilter.java`).
3. **RGPD opérationnel côté données** : consentement art. 9 à l'onboarding (`GdprService`, `Athlete`,
   `InvitationAcceptRequest`), export portabilité `GET /me/export`, suppression `DELETE /me`
   (`AthletePortalController.java:402`).
4. **Portail athlète offline-first réel** : file de retours en `localStorage` rejouée au retour réseau
   (`feedback-queue.service.ts`, `network-status.service.ts`), états `loading/ready/error`
   (`today.component.ts:69`), double séance/jour désormais gérée (cartes multiples, `StrengthCard`).
5. **Discipline d'ingénierie** : schéma piloté exclusivement par Liquibase (`ddl-auto: none`), DTO
   Request/Response séparés, composants standalone + signals + OnPush, gestion d'erreurs HTTP centralisée
   (`error.interceptor.ts`), **0 `console.log`** parasite dans le front.
6. **Documentation abondante et honnête** : `OPERATIONS.md` (backups, Sentry, restauration testée),
   `DEPLOIEMENT.md`, blueprint UX détaillé, audit interne transparent sur la dette.
7. **CI complète** : back `verify` + smoke test **PostgreSQL réel** (attrape les écarts H2↔PG au
   démarrage), front build AOT + Karma headless (`.github/workflows/ci.yml`).

---

## 4. Faiblesses majeures (transversales)

### 🔴 F1 — Le rafraîchissement de session n'est pas branché → **déconnexion forcée ~15 min**
**C'est le point le plus grave et il est invisible en démo courte.**
- Le back émet un access token à **TTL 900 s = 15 min** (`application.yml:69`, `JWT_ACCESS_TTL:900`) et
  expose un endpoint de rotation `POST /auth/refresh` fonctionnel (`AuthService.java:144`, avec rotation
  du refresh).
- **Mais le front n'appelle jamais `/auth/refresh`.** Le refresh token est bien stocké
  (`auth.service.ts:135`, clé `darilab.refreshToken`) **puis jamais consommé** : recherche exhaustive
  dans `front/src/app`, `/auth/refresh` n'apparaît que dans la *liste de bypass* de l'intercepteur
  (`auth.interceptor.ts:6`) — aucun code ne l'invoque. Il n'y a **pas** d'`APP_INITIALIZER`, pas
  d'intercepteur de retry-on-401, pas de timer de refresh.
- Conséquence : dès qu'une requête part après expiration de l'access token, l'`error.interceptor.ts:25`
  reçoit un 401 et **déconnecte l'utilisateur** (« Session expirée, veuillez vous reconnecter »).
- **Impact réel** : un athlète qui ouvre sa séance, court, puis revient noter son RPE **est déconnecté** ;
  un coach qui planifie une semaine **est éjecté en plein travail**. Pour une population non accompagnée,
  c'est un motif d'abandon immédiat et un flot de « ça me déconnecte tout le temps ».
- Cette faiblesse **contredit** le README (« JWT (access) + rotation refresh ») et l'audit de juin
  (« rotation refresh ») : la rotation existe côté serveur mais est **morte côté client**.

### 🔴 F2 — Couverture de tests front quasi nulle (angle mort de régression)
2 specs (`app.component.spec.ts`, `toast.service.spec.ts`) pour **84 composants / 32 services**. Le
parcours-roi athlète (séance du jour, RPE online/offline, calendrier) n'a **aucun** test. C'était déjà
le bloquant **B4** de l'audit de juin ; **il n'a pas été traité**. Toute évolution casse en silence.

### 🔴 F3 — Aucune base légale pour un service public collectant des données de santé
Aucun fichier de **politique de confidentialité**, **CGU**, **mentions légales**, **politique cookies**,
ni **LICENSE** dans le dépôt (recherche `privacy/terms/cgu/mentions/confidential` → 0 résultat ;
`LICENSE` absent ; README : « licence à définir »). Or le CDC classe explicitement les données physio
en **catégorie particulière RGPD art. 9** (`Cahier-des-charges.md §5.3`). Ouvrir au public sans ces
documents est un **risque juridique** réel (France/UE), pas un détail cosmétique.

### 🟠 F4 — Protection anti-abus insuffisante pour du self-service ouvert
Voir §9 : pas de captcha à l'inscription/login, endpoint `/public/password-reset` **non couvert** par le
rate-limiter, rate-limiter **en mémoire** (non partagé multi-pod, basé sur `X-Forwarded-For`).

### 🟠 F5 — Infra mono-instance non prête pour la charge annoncée (« 1000 users demain »)
Schedulers `@Scheduled` **sans ShedLock** (Strava sync, rappels J-1, digest alertes) → **doublons**
d'emails/imports dès 2 instances ; SSE avec émetteurs **en mémoire** (mono-pod) ; pièces jointes en
`bytea` en base. Détail §12. La doc **surestime** l'état (elle mentionne ShedLock — absent du code, §8).

### 🟠 F6 — Aucune mesure produit (analytics)
Aucun outil d'analytics produit (PostHog/Plausible/GA/Amplitude absents). On ne pourra **pas** mesurer
les KPI de bêta que le CDC lui-même exige (`§11` : % d'athlètes ayant connecté un appareil, % séances
loguées avec RPE, rétention). On pilotera la bêta à l'aveugle.

---

## 5. Fonctionnalités manquantes / incomplètes

Distinction **fait / supposé** : ci-dessous, chaque ligne est vérifiée dans le code sauf mention « (à confirmer) ».

| Manque | Statut | Preuve / source | Priorité bêta publique |
|---|---|---|---|
| **Refresh de session client** | ❌ non branché | `auth.interceptor.ts`, `auth.service.ts` (voir F1) | 🔴 Bloquant |
| **Politique de confidentialité / CGU / mentions légales** | ❌ absent | dépôt (voir F3) | 🔴 Bloquant |
| **Envoi mail activable en prod** | 🟠 désactivé par défaut | `application.yml:72` `MAIL_ENABLED:false` | 🔴 (config) |
| **Reset mot de passe de bout en bout** | 🟠 dépend du mail | `PasswordResetController.java` OK mais mail off | 🔴 (lié au mail) |
| **Captcha / anti-bot inscription** | ❌ absent | `AuthController`, pas de captcha | 🟠 |
| **Analytics produit** | ❌ absent | recherche outils (voir F6) | 🟠 |
| **Notifications push « séance du jour » validées device réel** | 🟠 SDK/SW présents, non validés | `push.service.ts`, `ATHLETE-ROADMAP.md §3` | 🟠 |
| **Import FIT (binaire Garmin)** | ❌ GPX/TCX seulement | README « Garmin/COROS prévus, non implémentés » | 🟡 (selon population) |
| **Garmin / COROS / Polar** | ❌ enum présent, non implémenté | README §Communication & données | 🔵 post-bêta |
| **Vue calendrier par groupe (coach)** | ❌ planif 1-à-1 | `app.routes.ts` (pas de route calendrier groupe) | 🟡 |
| **Pagination des listes non bornées** | 🟠 partielle (24 fichiers `Pageable`) | fil de messages non paginé (`ATHLETE-ROADMAP`, README dette) | 🟡 |
| **Wellness quotidien (sommeil/HRV/poids)** | ❌ hors périmètre | CDC §3.5 (priorité C) | 🔵 |
| **Facturation Stripe (abonnement)** | ❌ non livré | CDC §3.8/§10 | 🔵 post-bêta (mais requis pour monétiser) |
| **Page 404 dédiée** | ❌ wildcard → home | `app.routes.ts:322` `{ path:'**', redirectTo:'' }` | 🟡 |
| **Onboarding coach guidé (1er run)** | 🟠 non vérifié | composant `empty-state` existe ; pas de tour guidé | 🟡 |

---

## 6. Audit UX

**Constat général** : le portail **athlète** est la partie la plus finie (offline, états, ergonomie
tactile, invariants métier respectés). L'espace **coach** est riche mais dense. Les frictions qui feraient
abandonner un utilisateur de bêta ouverte :

1. 🔴 **Déconnexion intempestive (~15 min)** — cf. F1. C'est *la* friction rédhibitoire : elle casse le
   parcours quotidien (noter son RPE après la séance) et le travail long du coach (planifier une semaine).
2. 🟠 **Surface athlète très large** : le shell athlète expose **12 écrans** (`today, calendar, progress,
   history, activities, lactate, races, sync, performances, profile, messages, help` —
   `app.routes.ts:213-276`). Le blueprint UX impose « 5 items max » en bottom-nav (`ux-redesign §3.A`).
   Il faut vérifier que la navigation ne dépasse pas 5 destinations principales et que le reste est en
   drill-down — sinon densité et perte de repères (déjà signalé « 6 onglets » en juin, la surface a
   *augmenté* depuis).
3. 🟠 **Pas de page 404** : une URL erronée renvoie silencieusement à l'accueil (`app.routes.ts:322`) —
   déroutant, et masque les liens cassés.
4. 🟠 **Reset mot de passe sans mail** : le parcours existe (`forgot-password` → `reset-password/:token`,
   `app.routes.ts:26-35`) mais sans mail configuré, l'utilisateur qui a oublié son mot de passe est
   **bloqué dehors** sans recours self-service.
5. 🟡 **Route de dev exposée en prod** : `dev/ui-kit` (`app.routes.ts:53`) est accessible à tous — à
   retirer ou garder derrière un flag pour un service public.
6. 🟡 **États vides / erreurs** : bien gérés côté athlète (skeletons, « Repos aujourd'hui », bouton
   Réessayer). À auditer côté coach (dashboard vide d'un coach fraîchement inscrit, sans athlète).

**Ce qui est bon** (à ne pas casser) : feedback par toasts systématique, offline banner, bottom-sheets
mobiles, saisie RPE 1-tap, distinction donnée mesurée/estimée (`DataOriginTag`), thème sombre scopé.

---

## 7. Audit ergonomie & accessibilité

- 🟠 **Accessibilité modeste** : seulement **39 fichiers sur 84 composants** contiennent un attribut
  `aria-*/role/alt`. Le blueprint promet AA/AAA sur les données critiques, redondance couleur+forme+texte,
  navigation clavier et focus visibles (`ux-redesign §2, §5.1`) — l'intention est documentée mais la
  **couverture réelle est partielle** et non testée. Pour une bêta publique : au minimum labels sur tous
  les champs de formulaire, focus visibles, contrastes vérifiés, et redondance non-chromatique sur les
  statuts (douleur/charge) effectivement présente.
- 🟡 **Pas de framework i18n** : libellés français en dur (aucun `@angular/localize`/transloco réellement
  utilisé — les hits `i18n` sont des noms de composants). Acceptable en « français d'abord » (CDC §5.5),
  mais tout ajout d'anglais sera un refactor.
- 🟡 **Cohérence des composants** : un design system est amorcé (`shared/components/ui`, `physiology`,
  `empty-state`, `side-panel`, `bottom-sheet`) — bon socle. Vérifier que les écrans coach l'utilisent
  uniformément (le blueprint le prévoit ; l'application réelle n'est pas garantie sur 84 composants).
- 🟡 **Densité coach** : dashboard riche ; risque de « mur de KPI » que le blueprint met justement en
  garde. À valider sur écran 13".

> Note : l'ergonomie **tactile athlète** (cibles ≥ 44-56px, safe-areas iOS, clavier numérique) est, elle,
> conforme d'après l'audit de juin et le code (`today.component.ts`, sélecteurs 0-10).

---

## 8. Audit technique — écarts code ↔ documentation (important)

La consigne « le code prime sur la doc » a révélé **plusieurs divergences** où la doc **survend** l'état :

1. 🟠 **ShedLock annoncé, absent du code.** `OPERATIONS.md` et l'audit de juin laissent entendre que le
   verrou multi-pod est traité ou « à faire » ; en réalité **aucune dépendance ShedLock** n'est dans
   `back/pom.xml`, et `ReminderScheduler.java:16` porte seulement un commentaire « *passer à ShedLock en
   cas de scale-out* ». Les 3 schedulers (`StravaSyncScheduler`, `ReminderScheduler`,
   `AlertDigestScheduler`) sont des `@Scheduled` **nus**.
2. 🟠 **Refresh « rotation » annoncé, mort côté client** (cf. F1) — la rotation existe au back mais n'est
   jamais déclenchée par le front.
3. 🟠 **Testcontainers évoqué comme solution, absent.** `pom.xml` ne contient **pas** Testcontainers ;
   les tests tournent sur **H2 en mode PostgreSQL** (le CI ne fait qu'un *smoke* de démarrage sur PG).
   Le risque H2↔PG documenté reste **ouvert**.
4. 🟢 **Bon point de cohérence** : la doc est transparente sur les dettes connues (JWT en localStorage,
   token en query param SSE, `bytea`, pagination) — ces points sont réels et confirmés dans le code.

**Autres constats techniques** :
- ✅ `open-in-view: false` (`application.yml:16`) — évite les requêtes lazy hors transaction (bon).
- ✅ Health probes liveness/readiness activées, `show-details: when_authorized` (pas de fuite d'internes).
- 🟠 **Rate-limiter en mémoire** (`FixedWindowRateLimiter`) : non partagé entre instances, clé basée sur
  `X-Forwarded-For` (spoofable si le proxy ne réécrit pas l'en-tête) — `RateLimitFilter.java:59-65`.
- 🟠 **Token JWT en query param** pour le flux SSE notifications (`NotificationController.java:36`,
  `?access_token=`) — le jeton peut fuiter dans les logs d'accès/proxy. Dette documentée, à durcir.

---

## 9. Audit sécurité

**Solide pour une bêta fermée, insuffisamment durci pour l'ouverture publique.**

### Points forts (confirmés)
- Chiffrement santé + OAuth au repos (AES-256-GCM), garde-fou secrets au démarrage, anti-IDOR, CSP/HSTS,
  CORS allowlist, révocation JWT au logout (`TokenBlacklist`), rate-limit login/register.
- Reset mot de passe **anti-énumération** : réponse toujours 200 (`PasswordResetController.java:29-33`).
- Inscription coach **ne verrouille pas** sur email non vérifié (`AuthService.java:75-76`) → le
  self-service fonctionne même sans mail (bon design, l'email est une invitation douce à confirmer).

### Failles / durcissements requis pour un service **public**
| # | Sujet | Détail (sourcé) | Prio |
|---|---|---|---|
| S1 | **`/public/password-reset` non rate-limité** | `RateLimitFilter.shouldNotFilter` ne couvre que login/register/`accept` (`RateLimitFilter.java:36-41`). Un attaquant peut déclencher un **flood d'emails de reset** (coût + spam) et sonder les temps de réponse. | 🔴 |
| S2 | **Pas de captcha / anti-bot** | Inscription (`/auth/register`) crée un **club** à chaque appel ; 20 req/min/IP suffisent à polluer la base de bots. Pas de vérification humaine. | 🟠 |
| S3 | **Rate-limit non distribué** | En mémoire → inefficace dès 2 instances (F5). | 🟠 |
| S4 | **JWT + refresh en `localStorage`** | Exposition en cas de XSS (`auth.service.ts:126-137`). Dette documentée → cookie httpOnly à terme. | 🟠 |
| S5 | **Token en query param (SSE)** | Fuite possible dans logs (`NotificationController.java:36`). | 🟠 |
| S6 | **`state` OAuth Strava à signer** | Nonce anti-CSRF à ajouter (dette documentée). | 🟠 |
| S7 | **Route `dev/ui-kit` publique** | `app.routes.ts:53` exposée en prod. | 🟡 |
| S8 | **Purge du cache PWA au logout** | Sur appareil partagé, des réponses authentifiées peuvent rester en cache (dette documentée). | 🟡 |

> RGPD : le socle technique (consentement, export, suppression, chiffrement, pas de santé dans les mails)
> est là. **Le manquant est juridique/documentaire** (F3), pas technique.

---

## 10. Audit conformité / légal (spécifique bêta publique)

Bloc à part car **spécifique à l'ouverture au public** et souvent oublié :
- 🔴 **Aucune politique de confidentialité** (traitement des données santé art. 9, base légale, durées
  de conservation, sous-traitants Strava/Resend/Sentry, transferts hors UE éventuels).
- 🔴 **Aucune CGU / conditions d'utilisation** (responsabilité, disponibilité, propriété, résiliation).
- 🔴 **Aucune mention légale** (éditeur, hébergeur — obligatoire en France).
- 🟠 **Bannière cookies / consentement traceurs** : à évaluer selon les outils ajoutés (Sentry, analytics).
- 🟠 **Registre des traitements + DPA** avec les sous-traitants (Railway/Vercel/Resend/Sentry/Strava).
- 🟡 **Licence du code absente** (`LICENSE` manquant ; README « à définir »).
- 🟡 **Hébergement données de santé** : le CDC note « HDS non requis a priori, à confirmer juridiquement »
  (`Cahier-des-charges.md §5.3`) — **à trancher avant ouverture publique**.

---

## 11. Audit observabilité

- 🟢 **Crash reporting** : Sentry câblé back (`sentry-spring-boot-starter-jakarta`) et front
  (`@sentry/angular-ivy`), no-op tant que le DSN est vide (`application.yml:50-55`). **Reste à fournir
  les DSN en prod** — sinon on ne verra pas les 500 avant l'athlète.
- 🟢 **Health/metrics** : Actuator exposé (`health,info,metrics`), probes liveness/readiness.
- 🔴 **Aucun analytics produit** (F6) : impossible de mesurer activation, rétention, % de RPE saisis —
  les KPI que le CDC §11 exige pour juger la bêta.
- 🟠 **Logs** : pas de traçabilité centralisée documentée comme active (rétention/agrégation à câbler,
  checklist `OPERATIONS.md §6` non cochée par le code). Pas de `requestId`/tracing distribué.
- 🟠 **Alerting** : à configurer côté Sentry + uptime (BetterStack/UptimeRobot sur `/actuator/health`) —
  documenté mais non provisionné dans le repo.

---

## 12. Audit performance & scalabilité

- 🟢 **Bonnes bases** : `open-in-view:false`, agrégations serveur pour les graphes de charge, front lazy
  (`loadComponent` partout), OnPush + signals, budgets CSS configurés (`angular.json`).
- 🟠 **Pagination non généralisée** : le fil de messages est chargé en entier (dette documentée) ;
  24 fichiers utilisent `Pageable` mais les listes non bornées (messages, résultats, historiques)
  monteront en charge.
- 🔴 **Schedulers sans verrou (multi-instance)** : `StravaSyncScheduler` (toutes les 30 min),
  `ReminderScheduler` (18h), `AlertDigestScheduler` (7h) sans ShedLock → **doublons d'emails et
  d'imports** dès qu'on passe à 2 pods pour encaisser la charge. **Contradiction directe avec le
  scénario « 1000 users demain »** : soit on reste mono-instance (fragile, pas de HA), soit on scale et
  on double les notifications.
- 🟠 **SSE mono-instance** : émetteurs en mémoire (`MessageStreamService`, `NotificationStreamService`) →
  un athlète connecté au pod A ne reçoit pas un event émis via le pod B. Nécessite Redis pub/sub pour scaler.
- 🟠 **Pièces jointes en `bytea`** : stockage en base des images/PDF messagerie → gonfle la DB, coûteux à
  sauvegarder ; passer à S3 avant volume.
- 🟡 **Import Strava par polling** (cron 30 min) au lieu de webhook → latence + charge API inutile.

---

## 13. Audit qualité de code & tests

| Élément | État | Preuve |
|---|---|---|
| Tests **back** | 🟢 151 tests, moteurs + intégration MockMvc | `mvn verify` vert, 59 fichiers `src/test` |
| Tests **front** | 🔴 **2 specs** | `app.component.spec.ts`, `toast.service.spec.ts` |
| Tests **E2E** | 🔴 aucun | pas de Playwright/Cypress (README dette) |
| Tests sur **PG réel** | 🟠 smoke seulement | H2 en test, PG au démarrage CI (F8/§8) |
| Qualité front | 🟢 standalone/signals/OnPush, 0 console.log | revue code |
| Qualité back | 🟢 DTO séparés, engines purs, Liquibase strict | revue code |
| Dette **documentée** | 🟢 transparente | README §Limites, audit juin |

**Risque n°1 qualité** : sans tests front, chaque évolution du portail athlète (le parcours-roi) peut
régresser silencieusement. Minimum bêta : specs sur chargement séance du jour, soumission RPE
**online et offline/queue**, état d'erreur, déplacement calendrier — + un smoke E2E du login→today.

---

## 14. Audit documentation

- 🟢 **Développeur** : README riche, `Techno.md`, `PLAN-IMPLEMENTATION.md`, `Design.md`, blueprint UX,
  `Claude.md` (conventions). Excellent pour l'onboarding dev.
- 🟢 **Ops** : `OPERATIONS.md` (backups + restauration testée, Sentry pas-à-pas, Actuator, CI),
  `DEPLOIEMENT.md` (Railway/Vercel, variables, CORS). Rare à ce stade.
- 🟢 **Utilisateur** : centre d'aide intégré par profil (`/athlete/help`, `/app/aide`, `/admin/aide`) avec
  recherche + export PDF (`help/help-content.ts`).
- 🟠 **API** : Swagger/OpenAPI exposé, mais pas de doc API publiée/versionnée pour intégrateurs (non requis bêta).
- 🔴 **Manques** : documentation **légale** (§10), **runbook incident** (que faire si 500 en masse, si la
  DB sature, procédure de reset mot de passe manuel admin), **statut/changelog public** pour les bêta-testeurs.
- 🟠 **Doc partiellement en avance sur le code** (§8) : à corriger pour ne pas induire en erreur.

---

## 15. Risques pour une bêta ouverte — « si 1000 utilisateurs arrivent demain »

| Que va-t-il se passer ? | Cause (sourcée) | Gravité |
|---|---|---|
| **Vague de « ça me déconnecte sans arrêt »** | Refresh non branché, TTL 15 min (F1) | 🔴 Critique |
| **Athlètes bloqués dehors après oubli de mot de passe** | Mail off par défaut (F5/B1) | 🔴 Critique |
| **Bots créent des clubs/comptes** | Pas de captcha (S2) | 🟠 Élevé |
| **Flood d'emails de reset** | `/password-reset` non rate-limité (S1) | 🟠 Élevé |
| **On ne sait pas ce qui marche/casse** | Pas d'analytics produit (F6), Sentry à activer | 🟠 Élevé |
| **Emails/imports en double** si on scale à 2 pods | Schedulers sans ShedLock (§12) | 🟠 Élevé |
| **Messages temps réel qui « manquent »** si scale-out | SSE mono-instance (§12) | 🟠 Moyen |
| **DB qui gonfle / backups lents** | Pièces jointes `bytea` (§12) | 🟡 Moyen |
| **Exposition juridique** (contrôle CNIL, plainte) | Pas de politique confidentialité/CGU (F3) | 🔴 Critique (non-technique) |
| **Régressions silencieuses** à chaque déploiement | 2 specs front (F2) | 🟠 Élevé |
| **Frustration navigation athlète** | 12 écrans, bottom-nav dense (§6) | 🟡 Moyen |

**Synthèse du scénario** : techniquement l'app *tient* la charge en lecture/écriture (archi saine), mais
**l'expérience se dégrade fortement** (déconnexions, comptes bloqués), **on pilote à l'aveugle**
(pas d'analytics), on **ne peut pas scaler proprement** (mono-instance), et on **s'expose juridiquement**.

---

## 16. Liste exhaustive des actions à réaliser

Chaque action : **priorité · impact utilisateur · impact technique · complexité (S/M/L/XL) · risque · composants · dépendances**.

### 🔴 Bloquants avant bêta ouverte

| ID | Action | Prio | Impact util. | Impact tech. | Cplx | Risque | Composants | Dépend. |
|---|---|---|---|---|---|---|---|---|
| A1 | **Brancher le refresh token** (intercepteur retry-on-401 → `/auth/refresh`, file d'attente des requêtes, refresh silencieux + logout seulement si refresh échoue) | 🔴 | Très fort | Moyen | **M** | Moyen (concurrence 401) | `auth.interceptor`, `error.interceptor`, `auth.service` | — |
| A2 | **Activer un mail réel en prod** (Resend, domaine vérifié) + tester invitation/reset bout-en-bout ; prévoir reset manuel admin en secours | 🔴 | Fort | Faible | **S** | Faible | config, `NotificationService` | compte Resend |
| A3 | **Rédiger politique de confidentialité + CGU + mentions légales** (données santé art. 9, sous-traitants) et les lier dans l'UI (footer, onboarding) | 🔴 | Moyen | Faible | **M** | Faible (mais juridique) | front (pages statiques), légal | conseil juridique |
| A4 | **Rate-limiter `/public/password-reset`** (et généraliser aux routes publiques sensibles) | 🔴 | Faible | Faible | **S** | Faible | `RateLimitFilter` | — |
| A5 | **Câbler Sentry en prod** (DSN back+front) + uptime monitor + vérifier backups DB | 🔴 | Indirect | Moyen | **S** | Faible | config, ops | Sentry, Railway |
| A6 | **Tests front du parcours-roi** (séance du jour, RPE online/offline, erreur, déplacement) + 1 smoke E2E | 🔴 | Indirect | Moyen | **M** | Faible | `today`, `feedback-queue`, Karma/(Playwright) | — |

### 🟠 Important (avant ou tout début de bêta)

| ID | Action | Prio | Cplx | Composants |
|---|---|---|---|---|
| A7 | **Captcha / anti-bot** sur register (+ login après N échecs) | 🟠 | S | `AuthController`, front |
| A8 | **Analytics produit** (PostHog/Plausible self-host, RGPD-friendly) sur activation/rétention/RPE | 🟠 | M | front, infra |
| A9 | **Rate-limit distribué** (Redis) OU rester mono-instance assumé pendant la bêta | 🟠 | M | `RateLimitFilter` |
| A10 | **ShedLock sur les 3 schedulers** (ou garder mono-instance documenté) | 🟠 | S | `scheduler/*`, `pom.xml`, migration table lock |
| A11 | **Page 404 dédiée** + retirer/flag `dev/ui-kit` en prod | 🟠 | S | `app.routes.ts` |
| A12 | **Réduire la bottom-nav athlète à ≤ 5** destinations, reste en drill-down | 🟠 | M | `athlete-shell`, routes |
| A13 | **Runbook incident** (500 en masse, DB, reset manuel) + purge PWA au logout | 🟠 | S | docs, `update.service` |
| A14 | **Valider push « séance du jour »** sur device réel (iOS PWA inclus) + purge souscriptions mortes | 🟠 | M | `push.service`, SW, back |

### 🟡 Amélioration (pendant la bêta)

| ID | Action | Cplx |
|---|---|---|
| A15 | Paginer le fil de messages et les listes non bornées | M |
| A16 | Passer les pièces jointes en stockage objet (S3) | M |
| A17 | SSE multi-instance (Redis pub/sub) si scale-out | L |
| A18 | Testcontainers PostgreSQL en CI (fermer H2↔PG) | M |
| A19 | Vue calendrier par groupe (coach) | M |
| A20 | Import FIT ; `state` OAuth Strava signé ; webhook Strava | M/L |
| A21 | Audit accessibilité complet (aria, focus, contrastes, clavier) | M |
| A22 | JWT en cookie httpOnly ; jetons SSE/pièces jointes courts signés | L |

### 🔵 Idées post-bêta

Wellness/HRV · facturation Stripe · Garmin/COROS/Polar · i18n anglais · app native · modèles de plans.

---

## 17. Roadmap priorisée (par phases)

### Phase 1 — Bloquants (ouvrir la bêta publique) · ≈ 1 à 2 semaines
Ordre recommandé : **A1 → A2 → A4 → A5 → A6 → A3** (A3 en parallèle, dépend du juridique).
- **Objectif** : rendre le compte **autonome et stable** (session qui tient, reset qui marche),
  **protégé** (rate-limit reset), **observable** (Sentry) et **non régressif** (tests parcours-roi),
  **et légalement ouvrable** (docs).
- **Justification** : ce sont les points qui, seuls, transforment une démo réussie en bêta frustrante ou
  risquée. A1 est le plus rentable (un bug, impact maximal).
- **Sortie de phase** : un utilisateur inconnu peut s'inscrire, rester connecté une journée, récupérer
  son mot de passe seul, et une erreur serveur remonte dans Sentry.

### Phase 2 — Qualité & anti-abus · ≈ 1 semaine
A7 (captcha) → A8 (analytics) → A10 (ShedLock) → A9 (rate-limit distribué) → A13 (runbook) → A14 (push).
- **Objectif** : encaisser le public sans abus, **mesurer** la bêta, décider mono/multi-instance en connaissance.

### Phase 3 — Polish UX/ergonomie · en continu pendant la bêta
A11 (404 + retrait ui-kit) → A12 (bottom-nav ≤5) → A21 (accessibilité) → A15 (pagination) → A19 (calendrier groupe).

### Phase 4 — Après la bêta / durcissement & croissance
A16 (S3) → A17 (SSE Redis) → A18 (Testcontainers) → A20 (FIT/Strava webhook) → A22 (cookie httpOnly) →
puis features 🔵 (wellness, Stripe, Garmin, i18n, natif).

---

## 18. Estimation du niveau de préparation

| Cible | Préparation | Commentaire |
|---|---|---|
| **Bêta fermée encadrée (10-30 athlètes, 1-3 coachs)** | **~80 %** | Proche du GO : reste surtout A2 (mail) + A5 (Sentry) + A1 fortement conseillé. Cohérent avec l'audit de juin. |
| **Bêta ouverte publique (self-service, centaines/milliers)** | **~62 %** | NO-GO en l'état. A1 (bug refresh) + A3 (légal) + A4/A7 (anti-abus) + A6 (tests) + A8 (analytics) manquants. |

**Détail du 62 %** (pondéré) : cœur métier & archi 🟢 (très haut) ; sécurité applicative 🟢/🟠 ; UX
athlète 🟢 mais **plombée par le bug de session** ; conformité légale 🔴 (proche de 0) ; anti-abus 🟠 ;
observabilité produit 🔴 ; tests front 🔴 ; scalabilité 🟠. La note est tirée vers le bas par des
**manques ciblés à fort impact**, pas par une faiblesse structurelle.

---

## 19. Les 10 actions au meilleur ratio impact / effort

| Rang | Action | Effort | Impact | Pourquoi c'est rentable |
|---|---|---|---|---|
| 1 | **A1 — Brancher le refresh token** | M | 🔴🔴🔴 | Un seul bug ; supprime *la* friction n°1 (déconnexion 15 min) invisible en démo. |
| 2 | **A2 — Activer Resend en prod** | S | 🔴🔴 | Débloque reset + invitations ; pure config. |
| 3 | **A4 — Rate-limit `/password-reset`** | S | 🟠🟠 | Ferme un vecteur d'abus/email-bomb en quelques lignes. |
| 4 | **A5 — DSN Sentry + uptime + backups** | S | 🟠🟠 | On voit enfin les 500 avant l'athlète ; config. |
| 5 | **A11 — 404 dédiée + retrait `dev/ui-kit`** | S | 🟠 | Deux quick-wins UX/sécu triviaux. |
| 6 | **A10 — ShedLock schedulers** | S | 🟠🟠 | Débloque le scale-out sans doublons d'emails ; petite dépendance. |
| 7 | **A7 — Captcha register** | S/M | 🟠🟠 | Indispensable dès qu'on ouvre au public (bots). |
| 8 | **A6 — Tests front parcours-roi** | M | 🟠🟠 | Filet anti-régression sur la partie la plus utilisée. |
| 9 | **A8 — Analytics produit** | M | 🟠🟠 | Sans ça, la bêta ne produit aucune donnée d'apprentissage (KPI CDC §11). |
| 10 | **A3 — Politique conf. + CGU + mentions** | M | 🔴 | Débloque légalement l'ouverture ; effort surtout rédactionnel/juridique. |

> Note : A3 est classé 10 par *ratio* (effort de rédaction/juridique non trivial), mais c'est un
> **bloquant dur** — il doit être lancé **en parallèle** dès la Phase 1, pas repoussé.

---

## 20. Conclusion — « Cette application est-elle prête pour une bêta ouverte publique ? »

**Non — pas encore, mais elle en est proche, et l'écart est parfaitement franchissable.**

Le produit a ce qui est *difficile* à construire : un **cœur métier physiologique solide et testé**, une
**architecture multi-tenant propre et sécurisée au repos**, un **portail athlète mobile de qualité**, et
une **discipline d'ingénierie** (Liquibase strict, CI, docs ops) rare à ce stade. Sur le fond, ce n'est
pas une coquille : c'est une base sérieuse.

Ce qui manque pour l'**ouverture publique** n'est pas structurel — c'est un ensemble de **manques ciblés
à fort impact** :
1. **Un vrai bug bloquant, invisible en démo** : la session ne se rafraîchit pas, donc l'utilisateur est
   **déconnecté toutes les ~15 minutes**. À lui seul, il transformerait une bêta ouverte en avalanche de
   frustration. C'est la priorité absolue, et c'est un correctif de complexité **moyenne**.
2. **L'absence de base légale** (confidentialité, CGU, mentions) pour un service public qui manipule des
   **données de santé** — un risque juridique, pas cosmétique.
3. **Le self-service non protégé** (captcha, rate-limit du reset) — inévitablement exploité en public.
4. **L'aveuglement produit** (pas d'analytics) — on ne saurait même pas si la bêta réussit.
5. **Le filet de sécurité de tests front** (toujours 2 specs) et la **config d'exploitation** (mail,
   Sentry, backups) à finaliser.

Aucun de ces points n'est un chantier de plusieurs mois. **Une Phase 1 de 1 à 2 semaines** (les 6
bloquants §16) suffit à faire basculer le verdict, et le produit est **d'ores et déjà à ~80 % pour une
bêta fermée encadrée** — la voie recommandée pour commencer à apprendre avec de vrais utilisateurs
pendant que la Phase 1 se termine.

**Recommandation finale** :
- **GO immédiat** possible pour une **bêta fermée accompagnée** après A2 + A5 (+ A1 fortement conseillé).
- **NO-GO pour la bêta ouverte publique** tant que la **Phase 1 (A1→A6)** n'est pas livrée et que la
  **base légale (A3)** n'est pas en place.

---

> **Faits vs suppositions** — Cet audit s'appuie sur la lecture directe du code (chaque constat majeur est
> sourcé par un fichier/ligne). Les rares points non vérifiés dans le code sont signalés « (à confirmer) »
> ou renvoyés au conseil juridique (hébergement HDS, base légale). Aucune fonctionnalité inexistante n'a
> été inventée. La couverture de tests **back** n'a pas été ré-exécutée dans cet audit (build long) ; elle
> est réputée verte sur la foi de la CI et de l'audit de juin — à re-confirmer par `mvn verify` avant release.
