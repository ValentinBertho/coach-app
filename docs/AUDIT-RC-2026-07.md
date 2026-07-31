# Audit RC — DARI Lab (juillet 2026)

> Revue de la release candidate avant l'ouverture de la bêta. Complète
> `AUDIT-BETA-READINESS-2026-07.md`, qui portait sur l'**activation opérationnelle**
> (Sentry, sauvegardes, uptime, juridique) : celui-ci porte sur le **code**.
>
> **État : lots 1 à 8 livrés.** Le lot 9 (comparaison répétition par répétition) reste à faire —
> c'est un chantier distinct, à démarrer après la bêta.

**Légende** : ✅ traité · ⏳ à faire · ℹ️ déjà en place avant l'audit

---

## Lot 1 — Fuite de données et parcours d'entrée 🔴 ✅

- [x] **1. Cache du service worker non purgé à la déconnexion.**
      `AuthService.logout()` vide désormais `caches.keys()` / `caches.delete()` en plus du
      `localStorage`. Le `dataGroup` de `ngsw-config.json` ne met plus en cache que la séance du
      jour de l'athlète (`/api/me/today`, prescriptions) au lieu de `/api/clubs/**` et
      `/api/me/**` — c'est-à-dire toute la surface métier, données de santé comprises
      (art. 9 RGPD). L'`assetGroup` `fonts`, qui référençait encore Google Fonts alors que les
      polices sont auto-hébergées, est supprimé.
- [x] **2. Erreurs de connexion/inscription invisibles.**
      `SILENT_PATTERNS` ne couvre plus que `/auth/refresh` et `/auth/me`. Les formulaires de
      connexion et d'inscription affichent le message du serveur sous le champ (401 identifiants,
      401 compte suspendu, 409 e-mail déjà utilisé, 429 rate limit, perte de réseau) via
      `core/utils/auth-error.ts`. Les routes dont l'écran rend l'erreur lui-même sont listées à
      part, pour ne pas doubler le message par un toast.
- [x] **3. E-mail d'invitation athlète jamais envoyé.**
      `NotificationService.notifyAthleteInvitation()` (calqué sur `notifyCoachInvitation`) est
      appelé depuis `AthleteService.invite()`. L'URL reste dans la réponse API (secours, et seul
      canal pour un athlète sans adresse) ; rien n'est envoyé si `athlete.email` est nul.

## Lot 2 — Cohérence métier 🔴 ✅

- [x] **4. Le serveur raisonnait en UTC.**
      `TZ=Europe/Paris` dans `back/Dockerfile` (**à répliquer sur Railway**) et un `ClockService`
      (`today()` / `now()` / `zone()`) remplace les `LocalDate.now()` / `LocalTime.now()`
      statiques dans les deux schedulers, le portail athlète et les services de charge, de plan,
      d'analytics et d'objectifs. `hibernate.jdbc.time_zone: UTC` est inchangé : c'est le
      stockage des instants, il est correct. Tests : `SchedulerTimeZoneTest`.
- [x] **5. ACWR faux pendant 4 semaines.**
      `LoadEngine.compute()` ne publie plus de `ratio` sous 21 jours d'historique **et** 8 séances
      sur 28 jours. `LoadMetrics` expose `historyDays` et `ratioReady` ; le front affiche
      « ACWR en construction — n/28 jours ». L'alerte ROUGE de `CoachDashboardService` disparaît
      d'elle-même, sa branche testant déjà `ratio != null`. Tests : `LoadEngineTest`,
      `CoachAlertsTest`.
- [x] **6. Cockpit du premier jour.**
      À zéro athlète actif, le cockpit est remplacé par trois étapes numérotées avec bouton
      (créer un athlète → renseigner son profil physio → planifier sa première séance), au lieu
      de « Tout le monde est en forme » suivi de quatre zones vides.

## Lot 3 — E-mails 🔴 ✅

- [x] **7. Gabarit transactionnel.** `MailTemplate` enveloppe les sept envois dans un e-mail
      complet : `<!doctype>`, table 600 px centrée, en-tête avec le logo, bouton en table (≥ 44 px),
      pied de page avec l'éditeur et le lien de gestion des notifications. Couleurs inline reprises
      des tokens de `front/src/styles.scss`. La version texte est **dérivée du HTML** (deux
      rédactions divergent toujours) et transmise à `ResendMailClient.send()`, avec `reply_to` et
      `List-Unsubscribe`. Tests : `MailTemplateTest`.

## Lot 4 — Espace athlète : rendre quelque chose 🔴 ✅

- [x] **8. L'allure n'était pas affichée.** `ActivityResponse.paceSPerKm` est calculé côté serveur
      (une seule valeur pour tous les écrans) et affiché dans « Mes activités ». Sur la carte de
      séance de l'historique, la séance ne porte pas le réalisé — il vit dans l'activité
      rapprochée — donc l'allure montrée est celle **visée**.
- [x] **9. L'athlète ne pouvait pas déclarer une indisponibilité.** `POST` et `DELETE` sur
      `/me/unavailabilities` (garde-fou anti-IDOR sur la suppression) + formulaire dans le profil
      athlète : motif, dates, commentaire. Le coach référent est notifié in-app, en push et par
      e-mail. Tests : `UnavailabilityControllerTest`.
- [x] **10. Texte trompeur sur « Mes activités ».** L'état vide ne renvoie plus vers un Strava
      « via ton coach » ni vers un Garmin inexistant : il pointe vers `/athlete/sync`.

## Lot 5 — Import Strava complet 🔴 ✅

- [x] **11. `StravaClient.StravaActivity` ne désérialisait que 8 champs.** Ajoutés :
      `max_heartrate`, `average_speed`, `average_cadence`, `average_watts`, `calories`,
      `suffer_score`, `gear_id`, `workout_type`, `pr_count` et `map.summary_polyline`. FC max,
      cadence, puissance et calories sont persistées (migration **062**).
- [x] **12. Ni carte ni temps en zone pour un athlète Strava.** `PolylineDecoder` décode le
      `summary_polyline` vers `routeJson` (algorithme Google, sans dépendance) et
      `GET /activities/{id}/streams?keys=time,heartrate,velocity_smooth` alimente `streamJson` au
      format de `GpxParser.buildStream()`. Quota respecté : les activités déjà connues sont
      écartées **avant** l'appel détail, et un 429 est journalisé sans casser la synchro. Côté
      front, carte et zones ne sont plus réservées aux fichiers déposés à la main.
      Tests : `PolylineDecoderTest`, `StravaControllerTest`.
- [x] **13. Rapprochement prévu/réalisé.** La durée entre dans le score de `MatchingService` (10 km
      en 40 min et 10 km prévus en 60 min ne matchent plus parfaitement). `PATCH
      .../activities/{id}/match` (corps `{workoutId}` ou `null`) est ouvert au coach **et** à
      l'athlète, avec recalcul des deux statuts : la séance abandonnée redevient PLANIFIÉE au lieu
      de rester « réalisée » sans rien pour l'attester. Tests : `MatchingServiceTest`,
      `ActivityControllerTest`.

## Lot 6 — Sécurité 🟠 ✅

- [x] **14.** `users.password_changed_at` (migration **063**) : tout jeton émis avant est rejeté
      par `JwtAuthenticationFilter` et par `refresh()`. Tests : `SessionRevocationTest`.
- [x] **15.** `InvitationAcceptRequest.password` : `@Size(min = 8, max = 100)` (+ `@Valid` sur le
      corps, qui manquait).
- [x] **16.** `CoachInvitationAcceptRequest.termsAccepted` : `@NotNull` + `@AssertTrue`. Le seul
      `@AssertTrue` sur un `Boolean` aurait laissé passer l'absence du champ.
- [x] **17.** `resolveToken()` n'accepte `?access_token=` que sur les URI se terminant par
      `/stream` ou `/attachment`.
- [x] **18.** `front/vercel.json` : `Content-Security-Policy`, `X-Content-Type-Options`,
      `Referrer-Policy`, `Permissions-Policy`, `X-Frame-Options`, HSTS. L'inlining du CSS critique
      est désactivé côté build : il émettait un `onload=` inline qu'il aurait fallu autoriser dans
      `script-src`.
- [x] **19.** Bucket dédié `auth-login` (5/min) + `LoginAttemptTracker` : compteur d'échecs **par
      compte** à délai progressif (30 s doublé à chaque échec, plafond 15 min).
      Tests : `LoginAttemptTrackerTest`.

## Lot 7 — Bêta ouverte 🟠 ✅

- [x] **20. Quota de stockage.** Les pièces jointes sont rattachées à leur club (migration **064**,
      avec reprise des lignes existantes) et plafonnées à 200 Mo par club
      (`STORAGE_CLUB_QUOTA_MB`). Dépassement → 413 nommant l'espace consommé.
      `GET /clubs/{clubId}/storage` expose le compteur. Tests : `MessageAttachmentTest`.
- [x] **21. Vérification d'e-mail.** Non bloquante en lecture, exigée pour **inviter un athlète**
      et **inviter un coach** — les deux actions qui font sortir un e-mail vers un tiers choisi par
      l'utilisateur (`@emailVerificationValidator.isVerified`).

## Lot 8 — Finitions 🟠 ✅

- [x] **22.** `GET /me/activities/{id}/time-in-zone` ; le composant `time-in-zone-bar` accepte un
      mode « self » et sert les deux côtés.
- [x] **23.** `GET /me/week-summary` + bloc « Ma semaine » sur l'écran athlète
      (« 32/45 km, 3 séances sur 5 »). Les séances de repos ne sont pas comptées.
- [x] **24.** Open Graph + Twitter Card dans `front/src/index.html`, image `assets/og-image.png`
      (1200×630) aux couleurs de la marque.
- [x] **25.** Vraie page 404 (`features/public/not-found.component.ts`) au lieu de
      `{ path: '**', redirectTo: '' }`.
- [x] **26.** `/dev/ui-kit` et `/dev/api` derrière `adminGuard`.
- [x] **27.** `errorInterceptor` exploite `fieldErrors` du `GlobalExceptionHandler` (et traite
      aussi 413 et 429).
- [x] ℹ️ **28.** `PUT/PATCH` sur les objectifs athlète : **déjà en place avant l'audit**.
      `PATCH /me/races/{raceId}` existe (`AthletePortalController`) et l'écran « Mes objectifs »
      l'utilise déjà (`athlete-races.component.ts`). Rien à corriger.

## Lot 9 — Comparaison répétition par répétition 🟢 ⏳

- [ ] **29.** Importer les laps Strava (`GET /activities/{id}/laps`), les aligner sur les blocs de
      la structure prescrite, afficher la comparaison série par série dans `workout-detail`, puis
      un score d'exécution fondé sur le respect des fourchettes d'allure, qui alimente l'adhérence
      de « Mes progrès ». **Chantier distinct, à démarrer après la bêta.**

---

## Correctif hors périmètre relevé en chemin

- [x] **Encodage des sources Java.** `back/pom.xml` ne définissait pas
      `project.build.sourceEncoding` : javac prend alors l'encodage de la plateforme, et dans un
      conteneur en locale POSIX (le cas de la CI) **tous les accents des libellés français** —
      e-mails et notifications compris — sont compilés en caractères de remplacement. Fixé à UTF-8.

---

## À faire côté exploitation (hors code)

- [ ] Poser `TZ=Europe/Paris` sur le service Railway (le `Dockerfile` ne couvre que les
      déploiements par image).
- [ ] Renseigner `MAIL_REPLY_TO` : sans elle, l'en-tête `List-Unsubscribe` se limite au lien de
      préférences (pas de `mailto:` de repli).
- [ ] Ajuster `STORAGE_CLUB_QUOTA_MB` si 200 Mo par club se révèle trop serré en bêta.
