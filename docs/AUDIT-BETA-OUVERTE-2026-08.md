# Audit de bêta ouverte — DARI Lab (août 2026, second passage)

> **Question posée** : l'application est-elle prête à être ouverte à des inconnus ?
>
> **Méthode** : builds réellement exécutés (`./mvnw clean verify`, `npm run build`, `npm test`),
> lecture du code de sécurité, comparaison automatisée du schéma Liquibase aux entités JPA,
> traçage des parcours coach / athlète / admin, relecture des moteurs face au README et au
> cahier des charges.
>
> **Périmètre** : uniquement ce que les audits existants ne disent pas, ou disent à tort.
> `AUDIT-BETA-OUVERTE-2026-07`, `AUDIT-BETA-READINESS-2026-07`, `AUDIT-RC-2026-07` et
> `AUDIT-TECHNIQUE-2026-08` ne sont pas répétés — ils sont **vérifiés**, et contredits quand
> le code ne les suit pas.

---

## Verdict en trois lignes

**Le consentement santé est le point qui empêche d'ouvrir.** Le validateur existe, il est bien
écrit, il documente lui-même un périmètre de quatre familles de données — et il n'est branché que
sur une. Le retrait, de son côté, efface la douleur mais pas la fatigue, et ignore toute la
préparation physique.

**Deux correctifs d'août n'ont été appliqués qu'à moitié.** Le plafond de flux SSE a été posé sur
le compteur de notifications et pas sur la messagerie ; le plafond d'envoi d'e-mails couvre les
routes authentifiées et pas les routes anonymes — c'est-à-dire pas `/auth/register`, qui est
justement ce qu'on ouvre le jour de l'ouverture.

**Le reste tient.** Build back vert (295 tests), front vert (63 tests), schéma cohérent avec les
entités, anti-IDOR du portail athlète solide, en-têtes de sécurité corrects côté app.

---

## 0. Ce qui a été vérifié et qui tient

| Contrôle | Résultat |
|---|---|
| `./mvnw clean verify` | **BUILD SUCCESS** — 295 tests, 0 échec, 0 erreur (21 min) |
| `npm run build` | **OK** — 3 warnings (budget de bundle, `leaflet`/`localforage` non ESM) |
| `npm test` | **63/63** (Karma headless) |
| Schéma Liquibase ↔ entités JPA | **50 tables, aucun écart** (comparaison colonne par colonne, `@Column` / `@JoinColumn` / dérivation snake_case) |
| Anti-IDOR portail athlète `/me/**` | **solide** — chaque accès par identifiant refiltre sur `principal.athleteId()` : activités (`ActivityService:56,279,297`), objectifs (`RaceObjectiveService:87`), indisponibilités (`UnavailabilityService:73`), temps-en-zone (`TimeInZoneService:51`), pièces jointes (`MessageService:245`) |
| En-têtes de sécurité de l'app | **corrects** — `front/vercel.json` porte CSP, HSTS, `nosniff`, `frame-options`, `Permissions-Policy`. À noter : la CSP du `SecurityConfig` ne protège que l'API, c'est Vercel qui couvre l'app |
| Pièces jointes | **pas de XSS stockée** — allowlist png/jpeg/gif/webp/pdf (`MessageService:143`) + `nosniff` par défaut Spring Security |
| Moteurs vs README | **conformes** — Riegel `^1.06`, Nuzzo constante 104.9, ACWR unifié course + force (`AthleteLoadService:110-136`), `FormStatusEngine` sans RPE |

---

## 🔴 Bloquant — à faire avant d'ouvrir

### 1. Le garde-fou de consentement santé ne couvre qu'un quart de son propre périmètre

`HealthDataConsentValidator` définit lui-même sa portée dans sa javadoc : « mesures de lactate,
niveaux de douleur et de fatigue, indisponibilités pour raison médicale, notes médicales ».

Il n'est appelé **qu'une fois dans tout le dépôt** : `LactateTestService.java:79`.

Restent sans contrôle :

| Donnée | Où elle s'écrit |
|---|---|
| Notes médicales du profil | `AthleteService.java:259` |
| Motif d'indisponibilité (blessure / maladie) + commentaire | `UnavailabilityService.java:43` (coach) et `:59` (athlète) |
| Douleur et fatigue de séance | `WorkoutService.submitFeedback`, `DailyCheckInService.save`, `StrengthScheduleService.submitFeedback` |

Le scénario que le validateur dit exister pour empêcher est donc toujours ouvert : le coach crée
un athlète, écrit « douleur au genou droit » dans ses notes médicales, planifie une indisponibilité
« blessure » — puis l'invite. Et après un retrait de consentement, rien n'interdit de tout ressaisir
le lendemain.

**Contredit** `AUDIT-TECHNIQUE-2026-08` §3.2, qui annonce le validateur « appelé avant la collecte ».

**Effort** : ~0,5 j (injection dans trois services + tests d'accès sur le modèle de `HealthConsentTest`).

### 2. Le retrait de consentement n'efface pas ce qu'il annonce

`GdprService.withdrawHealthConsent` (`GdprService.java:149`) :

- ligne 172 : efface `workout.pain` — **mais pas `workout.fatigue`**, qui existe
  (`Workout.java:73-74`) et que la javadoc du même bloc dit effacer (« Les douleurs **et fatigues**
  déclarées sont effacées des check-ins et des séances ») ;
- toute la préparation physique est ignorée : `ScheduledStrengthSession.sessionPain` /
  `sessionFatigue` et `StrengthResult.pain` survivent au retrait.

Un athlète qui exerce l'article 7-3 garde donc en base une partie exacte des données qu'il vient
de refuser, et le journal `[RGPD]` (ligne 189) affirme le contraire.

**Contredit** `AUDIT-TECHNIQUE-2026-08` §3.1 (« efface exactement ce que la politique désigne comme
donnée de l'article 9 »).

**Effort** : ~2 h + test de non-régression.

### 3. Rappels des bloquants déjà tracés, vérifiés toujours ouverts

| # | Constat | Où |
|---|---|---|
| 3a | `legalName` et `address` de l'éditeur toujours vides — art. 13 RGPD | `front/src/app/features/public/legal.component.ts:34-35` |
| 3b | Aucune sauvegarde planifiée : `ops/backup-db.sh` n'est appelé par aucun workflow ni cron du dépôt (seul `.github/workflows/ci.yml` existe) ; restauration jamais testée | signalé en **juillet**, inchangé |

---

## 🟠 Important — première quinzaine

### 4. Assignation d'un cycle de force : aucun contrôle au niveau de l'athlète

`POST /clubs/{clubId}/pp/cycles/{id}/assign/{athleteId}` — `StrengthCycleController.java:66` —
n'est protégé que par le `@PreAuthorize` de classe (`:31`), qui ne teste que le club.

`StrengthCycleService.assign` (`:70`) écrit alors N semaines de séances dans le calendrier de
l'athlète visé. Tout coach du club peut donc planifier chez un athlète **privé d'un collègue**, ou
chez un athlète sur lequel il n'a qu'une permission `READ`.

C'est un oubli, pas un choix : les deux routes équivalentes font le contrôle
(`TrainingPlanController.java:69` → `canWrite(#request.athleteId())`,
`CourseSessionController.java:73` → `canWrite(#athleteId)`), et `TrainingPlanService.applyToGroup`
(`:209`) filtre athlète par athlète.

**Effort** : 15 min + un test d'accès.

### 5. N'importe quel coach du club peut éjecter n'importe quel coach

`DELETE /clubs/{clubId}/members/{coachId}` (`ClubController.java:73`) : `@PreAuthorize` de classe,
club uniquement. `ClubMembershipService.removeCoach` (`:129`) ne protège que le rôle `OWNER`.

Un coach assistant peut donc retirer le `COACH_PRINCIPAL` et tous ses collègues. Même chose pour
`POST /members` (`:60`) : tout membre peut ajouter des coachs et déclencher des invitations par
e-mail. Le `ClubRole` existe en base et n'est jamais consulté en autorisation.

**Effort** : ~0,5 j (validateur de rôle club + tests).

### 6. La déconnexion ne révoque pas le refresh token

`AuthController.logout` (`:84`) ne blackliste que l'access token. Le refresh token — TTL **30 jours**
(`JwtService.java:38`) — reste valable côté serveur ; seule sa copie locale est effacée.

Deux conséquences : « se déconnecter » n'invalide rien pour un jeton déjà exfiltré, et
`TokenBlacklist` étant **en mémoire** (`TokenBlacklist.java:16`), chaque redéploiement Railway
ressuscite tous les refresh tokens rotés. La rotation de `AuthService.refresh:261` ne tient donc
que jusqu'au prochain déploiement.

Le mécanisme correct existe déjà à côté : `TokenFreshnessValidator` invalide par `passwordChangedAt`.

**Effort** : ~0,5 j (une colonne `sessions_invalidated_at` posée au logout, lue par le validateur
de fraîcheur — pas de Redis nécessaire).

### 7. Le plafond de flux SSE n'a été posé que sur la moitié des flux

`NotificationStreamService` a reçu en août `MAX_STREAMS_PER_USER = 6` avec fermeture du plus ancien.
`MessageStreamService.subscribe` (`:29`) n'a **rien** : ni plafond, ni purge, et sa clé est
`athleteId` — donc partagée par l'athlète et tous les coachs qui ouvrent le fil.

La cause est identique et documentée dans le runbook (le proxy Vercel coupe mal les connexions
longues, `EventSource` rouvre seul). Le rate limiting attrape bien ces routes depuis août, mais au
plafond authentifié de 300 req/min : un client en boucle peut donc empiler jusqu'à 300 émetteurs
par minute, retenus 30 min chacun.

**Effort** : 30 min (recopier le plafond de `NotificationStreamService`).

### 8. Ouvrir la bêta rouvre l'amplificateur d'e-mails, du côté anonyme

Le bucket `EMAIL_BUCKET` (3 envois/h) posé en août ne s'applique qu'aux routes **authentifiées**
(`RateLimitFilter.isEmailTriggering:118`). Les routes anonymes qui envoient un e-mail retombent sur
le plafond général de 20 req/min/IP (`RateLimitFilter.bucket:75-90`) :

- `/auth/register` → un e-mail de vérification par appel (`AuthService.java:110`) ;
- `/public/password-reset` → un e-mail par appel.

Tant que `REGISTRATION_MODE=invite` (défaut prod, `application-prod.yml:31`), l'inscription est
close. **Ouvrir la bêta, c'est précisément passer ce réglage à `open`** — et exposer alors le quota
Resend (100/jour, partagé avec les réinitialisations et les invitations athlète) à 20 envois par
minute et par IP. Quelques minutes suffisent, et ce sont les liens critiques qui tombent avec.

**Effort** : ~1 h (bucket dédié aux routes anonymes à e-mail, quelques envois par heure et par IP).

---

## 🟢 Mineur

| # | Constat | Où | Effort |
|---|---|---|---|
| 9 | `purgeExpiredWindows()` oublie `emailLimiter` : sa table (clé par porteur de jeton, fenêtre 1 h) n'est jamais purgée — fuite mémoire lente | `RateLimitFilter.java:236-240` | 5 min |
| 10 | `GET /clubs/{clubId}/zones?athleteId=…` lit l'échelle de zones d'un athlète avec le seul contrôle club, sans `canRead` — dernière route athlète-scopée sans garde de niveau athlète | `TrainingZoneController.java:45-47` | 10 min |
| 11 | Trois moteurs sans aucun test — les trois seuls du paquet `engine/`. `PlannedLoadEngine` alimente `workouts.planned_load_ua`, affiché au coach ; `CriticalSpeedEngine` produit la vitesse critique, qui pilote toutes les cibles de séance | `CriticalSpeedEngine`, `PlannedLoadEngine`, `PaceUtil` | 0,5 j |
| 12 | `PlannedLoadEngine` : javadoc « récupérations comprises » (`:23`), code qui les exclut explicitement (`:47-50`) | `PlannedLoadEngine.java` | 5 min |
| 13 | README désynchronisé : « 151 tests » (réel **295**), « 41 contrôleurs » (**47**), « 47 services » (**60**), « 11 moteurs » (**13**), « **65** migrations » ligne 119 **et** « **42** migrations » ligne 200 dans le même document (réel **67**), « ~236 endpoints » (**294**), « import GPX/**FIT** » alors que FIT n'est pas géré (`GpxParser.java:18`, front `accept=".gpx,.tcx"`) | `README.md` | 30 min |
| 14 | Bundle initial front à **608,75 kB** pour un budget de 500 kB — avertissement seulement, le build ne casse pas ; c'est une PWA mobile | `front/angular.json` | à arbitrer |
| 15 | `clubLevelFallback` accorde `WRITE` à tout coach du club dès qu'un athlète n'a pas de relation référente. Filet volontaire et documenté, mais c'est un *fail-open* : un bug de création d'athlète devient un partage silencieux | `AthleteAccessValidator.java:139-149` | surveiller |
| 16 | L'athlète ne peut changer ni son mot de passe, ni son nom, ni son adresse depuis la PWA, alors que `POST /auth/change-password` et `PATCH /auth/me` acceptent son rôle. Seul recours : « mot de passe oublié » | `front/.../athlete/profile.component.ts` | 0,5 j |

---

## Gravité réelle des « Limites connues » du README, pour une bêta ouverte

| Limite | Verdict | Pourquoi |
|---|---|---|
| **SSE mono-instance** | **tolérable** | Un seul pod Railway ; devient bloquant au premier scale-out, pas avant. Le vrai risque aujourd'hui n'est pas le multi-pod, c'est l'absence de plafond sur `MessageStreamService` (**#7**). |
| **Jeton en query param** (SSE + pièces jointes) | **tolérable, à fermer avant la sortie de bêta** | Rate-limité depuis août, restreint aux suffixes `/stream` et `/attachment` (`JwtAuthenticationFilter:89`). Mais l'access token part dans les journaux d'accès Vercel et Railway et dans l'historique du navigateur, avec un TTL d'**1 h** (`application.yml:71`, non surchargé en prod). |
| **Pagination** | **tolérable** | `MessageService.coachThread:47` et `athleteThread:113` chargent le fil entier. À la volumétrie d'une bêta (quelques dizaines de messages par athlète), sans conséquence. Le composant `paginator` existe déjà côté front. |
| **Pièces jointes en `bytea`** | **plus tard** | Le quota par club est en place (200 Mo, `application.yml`) et refusé proprement. Mais ces octets grossissent le `pg_dump` — et **la sauvegarde n'est pas planifiée** (**#3b**) : c'est le point à traiter, pas le stockage objet. |

---

## Écarts au cahier des charges (au-delà de Garmin/COROS)

Rien de bloquant — tout est en priorité **S** ou **C** au CDC, et assumé :

- **§3.3** Garmin / COROS / Polar : enum présent, non implémenté (déjà dit au README). **FIT** : annoncé
  dans le tableau de stack, non implémenté — seuls GPX et TCX le sont.
- **§3.3 / §6.3** Webhooks Strava : import par polling horaire, donc pas de vérification de signature
  de webhook (exigée en §5.2 — sans objet tant qu'il n'y a pas de webhook).
- **§3.5** Wellness : le check-in matinal couvre sommeil / fatigue / douleur ; **HRV, humeur et poids
  quotidien** ne sont pas saisissables (priorité C).
- **§3.8 / §10** Facturation et abonnements : rien (priorité C, hors MVP).
- **§3.9** Modules activables et logo de club : non implémentés (déjà tracé en juillet §1.4).
- **§5.1** « Pagination serveur sur les listes » : partiellement respecté (cf. tableau ci-dessus).

---

## Ordre d'exécution

**Avant d'ouvrir — ~1,5 j de code + 2 actions exploitant**
1. Brancher `HealthDataConsentValidator` sur les trois services manquants (**#1**).
2. Compléter l'effacement au retrait : `workout.fatigue`, force (**#2**).
3. Contrôle d'athlète sur l'assignation de cycle (**#4**).
4. Bucket anonyme sur les routes à e-mail, avant de passer `REGISTRATION_MODE=open` (**#8**).
5. Exploitant : identité de l'éditeur (**#3a**), planification + **test de restauration** des
   sauvegardes (**#3b**).

**Première quinzaine — ~1,5 j**
6. Plafond sur `MessageStreamService` (**#7**).
7. Révocation du refresh au logout (**#6**).
8. Rôles club en autorisation (**#5**).
9. Tests des trois moteurs orphelins (**#11**), purge `emailLimiter` (**#9**), `canRead` sur les
   zones (**#10**), README remis d'équerre (**#13**).

**Plus tard** — compte athlète complet (**#16**), jetons courts signés à la place du query param,
pagination des fils, stockage objet.

---

*Audit — DARI Lab, 3 août 2026. Builds exécutés, code lu, schéma comparé automatiquement aux
entités. Les constats déjà couverts par les audits antérieurs ne sont repris que lorsqu'ils sont
contredits par le code ou vérifiés toujours ouverts.*
