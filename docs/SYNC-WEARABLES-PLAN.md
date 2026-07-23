# Sync montres — Plan d'action (Strava / Garmin / Coros)

> **Objectif produit.** Rendre l'appli « quotidienne » en fermant les deux boucles de synchro :
> 1. **Entrant** (montre → appli) : les activités réalisées remontent **automatiquement** depuis
>    Strava / Garmin / Coros → suivi de charge sans saisie.
> 2. **Sortant** (appli → montre) : les séances prescrites sont **poussées sur la montre** de
>    l'athlète (comme Nolio / TrainingPeaks) → il a son programme du jour sur son poignet.
>
> Ce document est le plan d'exécution : ce qui existe déjà, ce qui manque, l'ordre de bataille,
> l'architecture cible, et les démarches **non techniques** (partenariats) qui conditionnent tout.

---

## 0. À lire AVANT d'écrire une ligne de code (le point qui change le projet)

Deux réalités des API constructeurs déterminent le périmètre réel et le calendrier. Se tromper ici
fait perdre des mois.

### 0.1 — Le flux sortant n'est PAS disponible partout

| Plateforme | Lire les activités (entrant) | Pousser une séance sur la montre (sortant) |
|---|---|---|
| **Strava** | ✅ oui (déjà fait) | ❌ **impossible** — l'API Strava ne permet pas d'écrire des séances structurées sur un appareil |
| **Garmin** | ✅ oui (Health/Activity API) | ✅ oui, via la **Training API** (workouts + planning) |
| **Coros** | ✅ oui (Open API) | ✅ oui, via la **Training / Open API** |

**Conséquence directe :** la fonctionnalité « à la Nolio » (programme sur la montre) passe
**obligatoirement par Garmin et Coros**, jamais par Strava. Strava reste le canal d'**import**
universel (beaucoup d'athlètes y agrègent déjà tout), mais c'est un cul-de-sac pour le sortant.

### 0.2 — Garmin et Coros exigent un partenariat approuvé (délai humain, pas technique)

Contrairement à Strava (inscription en self-service en 5 min), **Garmin et Coros ne donnent pas
d'accès API instantané** :

- **Garmin Developer Program** (`developer.garmin.com`) : il faut demander l'accès **produit par
  produit** (Health API, Activity API, **Training API**), **signer un accord**, décrire l'usage, et
  attendre une **validation manuelle**. Compter **plusieurs semaines**. La **Training API** (celle du
  push de séances) est la plus encadrée.
- **Coros** : accès **Open API** sur demande de partenariat (`opendev@coros.com` / formulaire
  partenaire). Validation manuelle également, délai variable.

> **⚠️ Action n°1, à lancer aujourd'hui, en parallèle de tout le reste :** déposer les demandes
> d'accès Garmin (Health + Activity + **Training**) et Coros (Open API). Le code peut être écrit
> pendant l'attente contre la doc/le bac à sable, mais **rien ne partira en prod sans ces feux
> verts**. C'est le chemin critique du projet.

### 0.3 — Ce que ça implique pour le séquencement

On ne bloque pas la livraison de valeur derrière des validations externes. D'où le découpage :

- **Lot A (rapide, 100 % sous ton contrôle) :** fiabiliser et compléter **l'entrant Strava**
  (webhook temps réel au lieu du polling). Déjà 90 % fait.
- **Lot B (entrant Garmin + Coros) :** dès les accès obtenus.
- **Lot C (sortant Garmin + Coros) :** la vraie nouveauté « Nolio ». Le plus fort effet « waouh »,
  mais dépend de la Training API → à démarrer côté démarches **immédiatement**, à coder ensuite.

---

## 1. État des lieux dans le code (ce qui existe déjà)

Bonne nouvelle : l'ossature multi-provider est **déjà posée**. On étend, on ne refond pas.

| Brique | Fichier | État |
|---|---|---|
| Enum fournisseurs | `entity/enums/DeviceProvider.java` | ✅ `STRAVA, GARMIN, COROS` déjà présents |
| Enum origine activité | `entity/enums/ActivitySource.java` | ✅ `MANUAL, FILE, STRAVA, GARMIN, COROS` |
| Connexion OAuth (jetons **chiffrés**) | `entity/DeviceConnection.java` | ✅ générique (athlete + provider), AES-256-GCM au repos |
| Import + déduplication | `service/StravaService.java`, `entity/Activity.java` | ✅ dédup par `(athlete, source, externalId)` |
| Client HTTP Strava | `integration/StravaClient.java` | ✅ OAuth + `listActivities` |
| Import périodique | `scheduler/StravaSyncScheduler.java` | ✅ cron `app.strava.sync-cron` (polling horaire) |
| Contrôleur athlète | `controller/StravaController.java` + portail athlète | ✅ « l'athlète connecte SA montre » (CDC §12) |
| Variables d'env | `.env.example` | ✅ `GARMIN_CLIENT_ID/SECRET`, `COROS_CLIENT_ID/SECRET`, `STRAVA_WEBHOOK_VERIFY_TOKEN` déjà réservées |
| Séance prescrite (source du sortant) | `entity/Workout.java` + `entity/WorkoutStep.java` | ✅ steps structurés + `calculatedPaces` (cibles JSON par athlète) |

**Ce qui manque :**
- Entrant Strava **temps réel** (webhook) — aujourd'hui polling horaire seulement.
- **Aucun** client Garmin ni Coros (ni entrant ni sortant).
- **Aucune** brique sortante (push de séance) pour aucun provider.
- Le modèle `DeviceConnection` est **entrant-only** dans sa sémantique actuelle : à généraliser
  (voir §3).

---

## 2. Architecture cible : une abstraction, trois implémentations

Le piège serait de dupliquer `StravaService` en `GarminService` puis `CorosService`. À la place, on
introduit **deux interfaces de port** (entrant / sortant) et une **fabrique par provider**. Ça
garde le métier (charge, dédup, matching séance↔activité) mutualisé et testable.

```
com.coachrun.integration.sync
├── ActivityProvider (interface)          ← ENTRANT
│     authorizeUrl(state), exchangeCode(code), refresh(conn),
│     fetchActivities(conn, afterEpoch) -> List<NormalizedActivity>
│     verifyWebhook(payload) / parseWebhook(payload) -> List<externalId>
│
├── WorkoutPushProvider (interface)       ← SORTANT
│     pushWorkout(conn, ProviderWorkout) -> providerWorkoutId
│     scheduleWorkout(conn, providerWorkoutId, date)
│     deleteWorkout(conn, providerWorkoutId)
│     supportsPush() -> boolean           (Strava renvoie false)
│
├── strava/StravaActivityProvider   implements ActivityProvider
├── garmin/GarminActivityProvider   implements ActivityProvider, WorkoutPushProvider
└── coros/CorosActivityProvider     implements ActivityProvider, WorkoutPushProvider
```

- `SyncService` (métier, provider-agnostique) reçoit le bon port via un `Map<DeviceProvider, …>`
  injecté par Spring. Il garde : dédup, `lastImportEpoch`, rafraîchissement de jeton, matching
  activité↔`Workout` (`matchedWorkoutId`), recalcul de charge.
- `StravaService` actuel est **refactoré** en `StravaActivityProvider` (le gros du code est déjà
  écrit, on le déplace derrière l'interface). Rétro-compat : garder les endpoints existants.
- **Types normalisés** internes (déjà quasi présents via `ActivityImportRequest`) :
  - `NormalizedActivity` = ce que renvoie `toImportRequest(...)` aujourd'hui.
  - `ProviderWorkout` = la séance traduite au format cible (voir §6).

> Règle projet respectée : **noms anglais dans le code, libellés FR dans l'UI** ;
> **toute** évolution de schéma passe par une **migration Liquibase** (jamais `ddl-auto`).

---

## 3. Migrations Liquibase à prévoir

Nouvelle migration `0NN-device-sync-outbound.yaml` (adapter le numéro à la suite des 42 existantes) :

1. **Généraliser `device_connections`** pour le sortant :
   - `scopes` (texte) — on stocke la liste réelle des scopes accordés (entrant vs sortant diffèrent).
   - `direction` **ou** deux booléens `can_import` / `can_push` — de quoi savoir si la connexion
     autorise le push (dépend des scopes OAuth accordés par l'athlète).
   - `provider_user_id` existe déjà (`providerAthleteId`) — OK pour le webhook mapping.
2. **Table `pushed_workouts`** (traçabilité du sortant, indispensable pour mettre à jour/supprimer) :
   ```
   pushed_workouts
   ├── id (BaseEntity)
   ├── workout_id        FK workouts        (la séance prescrite locale)
   ├── athlete_id        FK athletes
   ├── provider          enum DeviceProvider
   ├── provider_workout_id   varchar        (id renvoyé par Garmin/Coros)
   ├── provider_schedule_id  varchar null   (id de la planification si distinct)
   ├── status            enum {PUSHED, UPDATED, FAILED, DELETED}
   ├── pushed_at         timestamp
   └── last_error        varchar null
   UNIQUE (workout_id, provider)
   ```
   → permet le **repush idempotent** quand le coach modifie une séance déjà envoyée, et le retrait
   propre si la séance est supprimée/déplacée.
3. **Index** : `pushed_workouts(athlete_id, provider)`, `pushed_workouts(workout_id, provider)`.

Rappel qualité : les tests tournent sur **H2 mode PostgreSQL** + smoke sur **PG réel** en CI. Vérifie
la migration sur les deux (cf. `docs/OPERATIONS.md`).

---

## 4. Flux ENTRANT (montre → appli)

### 4.1 — Strava : passer du polling au webhook (Lot A, rapide)

Aujourd'hui : `StravaSyncScheduler` interroge Strava **toutes les heures**. Ça marche mais c'est
lent (latence jusqu'à 1 h) et coûteux en quota API. Strava fournit une **Webhook Events API** :

1. **Souscription** (une fois par appli, pas par athlète) : `POST /push_subscriptions` avec
   `callback_url` = `https://<api>/api/webhooks/strava` et `verify_token` =
   `STRAVA_WEBHOOK_VERIFY_TOKEN` (déjà réservé dans `.env`).
2. **Handshake** : Strava appelle ton endpoint en `GET` avec `hub.challenge` → tu renvoies le
   challenge (validation d'ownership).
3. **Événements** : à chaque activité créée/màj, Strava `POST` `{object_id, owner_id, aspect_type}`.
   Tu mappes `owner_id` → `DeviceConnection.providerAthleteId` → tu **pulles** l'activité
   (`GET /activities/{id}`) et tu réutilises l'import + dédup existants.
4. **Garde le scheduler** comme **filet de sécurité** (rattrapage des événements manqués), mais
   passe-le à une fréquence basse (ex. 1×/nuit).

Sécurité : valider le `verify_token`, répondre `200` en < 2 s (traitement asynchrone), idempotence
garantie par la dédup `(athlete, STRAVA, externalId)` déjà en place. Corrige aussi la dette connue
« `state` OAuth Strava à signer (nonce anti-CSRF) » notée dans `docs/AUDIT-BETA-2026-06.md`.

### 4.2 — Garmin entrant (Health + Activity API)

- **Auth** : OAuth (Garmin Connect — flux PKCE côté Health/Activity/Training API ; **à confirmer à
  l'onboarding**, Garmin fait évoluer l'auth). Stockage jetons dans `DeviceConnection` (déjà chiffré).
- **Réception des données** : Garmin fonctionne en **Ping/Push** : il appelle un endpoint que tu
  enregistres (`/api/webhooks/garmin`) pour te **notifier** qu'une activité est dispo, puis tu la
  **pulles** (Activity API) — même schéma que le webhook Strava. Format : résumé **JSON** + fichier
  **FIT** optionnel.
- **Mapping** → `NormalizedActivity` (distance, temps mobile, FC moyenne, D+). `source = GARMIN`,
  `externalId = summaryId Garmin`. Dédup identique.
- **FIT** : le résumé JSON suffit pour la charge (comme Strava aujourd'hui). Stocker le FIT pour le
  détail est un **plus** (résout aussi la dette « FIT non géré » de l'audit) mais **hors chemin
  critique** — le parser FIT peut venir en second temps.

### 4.3 — Coros entrant (Open API)

- **Auth** : OAuth 2.0. Jetons dans `DeviceConnection`.
- **Réception** : Coros pousse les données d'activité vers un **webhook** enregistré
  (`/api/webhooks/coros`) — même patron. Payload JSON.
- **Mapping** → `NormalizedActivity`, `source = COROS`. Dédup identique.

> Les trois providers convergent vers **le même patron webhook** (`/api/webhooks/{provider}`) +
> **le même import métier**. C'est tout l'intérêt de l'abstraction §2.

### 4.4 — Portail athlète (déjà en place, à étendre)

Le CDC §12 impose que **l'athlète connecte lui-même sa montre** (self-service dans le portail PWA).
`StravaService.*ForAthlete(...)` fait déjà exactement ça. On généralise l'UI « Connexion montre »
pour proposer **3 boutons** (Strava / Garmin / Coros), chacun lançant l'`authorizeUrl` du bon
provider. État affiché : connecté / « dernière sync il y a … » (le composant d'état existe déjà,
cf. `docs/Design.md` §« Sync en cours »).

---

## 5. Flux SORTANT (appli → montre) — la fonctionnalité « Nolio »

C'est la nouveauté forte. Disponible **Garmin + Coros uniquement** (§0.1).

### 5.1 — Scopes OAuth : l'athlète doit autoriser l'écriture

Le push exige des **scopes supplémentaires** (écriture d'entraînements) au moment du consentement
OAuth. Deux options :
- **Un seul consentement** demandant lecture **+** écriture (plus simple, recommandé).
- Consentement en deux temps (connexion d'abord, activation du push ensuite).

On stocke les scopes réellement accordés (`DeviceConnection.scopes`) et on en déduit `can_push`.
Si l'athlète n'a autorisé que la lecture → l'UI propose « Activer l'envoi vers ma montre » (re-consent).

### 5.2 — Traduire une `Workout` locale en séance constructeur

La source est déjà structurée et **déjà calculée par athlète** :

- `Workout` → titre, date, type (`WorkoutType`), `steps` ordonnés.
- `WorkoutStep` → `stepType` (`WARMUP/STEADY/REPETITION/RECOVERY/COOLDOWN`), `repetitions`,
  `zone` (`Z1..Z5`), `distanceM`, `durationS`, `notes`.
- `Workout.calculatedPaces` (JSON) → **cibles chiffrées** (allure/FC) déjà résolues pour CET athlète
  au moment de l'assignation. **C'est la clé** : on possède déjà les valeurs à envoyer, pas
  seulement des zones abstraites.

**Mapping conceptuel vers le format Garmin/Coros** (un `WorkoutMapper` par provider) :

| Modèle DARI Lab | Format montre (concept commun Garmin/Coros) |
|---|---|
| `WorkoutStep.durationS` renseigné | `durationType = TIME`, valeur = secondes |
| `WorkoutStep.distanceM` renseigné | `durationType = DISTANCE`, valeur = mètres |
| ni l'un ni l'autre | `durationType = OPEN` (lap manuel) |
| `stepType = WARMUP` | step `WARMUP` |
| `stepType = COOLDOWN` | step `COOLDOWN` |
| `stepType = RECOVERY` | step `RECOVERY/REST` |
| `stepType = REPETITION` avec `repetitions > 1` | **repeat group** (× N) englobant le/les steps |
| `stepType = STEADY` | step `INTERVAL/ACTIVE` |
| `zone Z1..Z5` + `calculatedPaces` | `targetType = PACE_ZONE` (ou `HEART_RATE_ZONE`) avec **min/max** issus des fourchettes calculées |
| `notes` | libellé/description du step |

Points d'attention :
- Le concept DARI Lab est **la fourchette (min–max)** ; Garmin/Coros acceptent des cibles basse/haute
  → mapping naturel (ne pas écraser en valeur unique).
- Choisir la **nature de cible** (allure vs FC) selon ce que l'athlète préfère / ce que la séance
  privilégie. Défaut raisonnable : **allure** en course, **FC** en endurance fondamentale.
- Les séances **force** (`WorkoutType.STRENGTH`) : Garmin gère les workouts de musculation, mais
  c'est un format distinct et secondaire → **exclure du lot 1** (push course d'abord).

### 5.3 — Envoyer et planifier

1. `pushWorkout(conn, providerWorkout)` → crée la séance chez le constructeur → renvoie
   `provider_workout_id`.
2. `scheduleWorkout(conn, provider_workout_id, date)` → la **planifie dans le calendrier Garmin
   Connect / Coros** à `Workout.scheduledDate` → elle se **synchronise sur la montre** de l'athlète
   automatiquement (c'est Garmin/Coros qui pousse vers le device).
3. Enregistrer la ligne `pushed_workouts` (idempotence).

### 5.4 — Cycle de vie : modif / déplacement / suppression

C'est là qu'on gagne ou perd la confiance des athlètes. Toute écriture locale sur une `Workout`
poussée doit se **répercuter** :

- Coach **modifie** la séance → repush : `deleteWorkout` + recréation, ou mise à jour si l'API le
  permet, puis update `pushed_workouts`.
- Athlète **déplace** la séance (`movedByAthlete`) → replanifier à la nouvelle date.
- Séance **supprimée** → `deleteWorkout` chez le provider.
- Déclenchement : soit **synchrone** à la sauvegarde (hook service), soit via une **file/`@Scheduled`**
  de réconciliation qui compare `workouts` ↔ `pushed_workouts` (plus robuste, résiste aux pannes
  réseau, cohérent avec l'esprit « scheduler » déjà en place). **Recommandé : réconciliation.**

### 5.5 — Déclenchement du push : qui, quand ?

- **Par défaut** : pousser automatiquement les séances **à venir** (fenêtre glissante ex. 7–14 jours)
  des athlètes ayant `can_push = true`. Un `WorkoutPushScheduler` (jumeau du `StravaSyncScheduler`)
  gère la fenêtre + les repush.
- **Réglage athlète** : bouton « Envoyer mon programme sur ma montre » (on/off) dans le portail.
- Éviter de pousser des séances passées ou l'historique complet (bruit sur la montre).

---

## 6. Endpoints & webhooks (vue API)

Nouveaux points d'entrée (à ajouter à l'OpenAPI existant) :

```
# ENTRANT — un webhook par provider (public, signé/vérifié, réponse < 2s, async)
GET  /api/webhooks/strava     # handshake hub.challenge
POST /api/webhooks/strava     # events
POST /api/webhooks/garmin     # ping/push
POST /api/webhooks/coros      # push

# CONNEXION (portail athlète, générique) — remplace/complète /strava
GET    /clubs/{c}/athletes/{a}/devices                       # état des 3 providers
GET    /clubs/{c}/athletes/{a}/devices/{provider}/authorize  # URL OAuth
POST   /clubs/{c}/athletes/{a}/devices/{provider}/connect    # échange code
DELETE /clubs/{c}/athletes/{a}/devices/{provider}            # révoquer (droit à l'oubli, CDC)

# SORTANT (push)
POST   /clubs/{c}/athletes/{a}/devices/{provider}/push       # (re)pousser les séances à venir
PUT    /clubs/{c}/athletes/{a}/push-settings                 # activer/désactiver l'envoi montre
```

Garder les endpoints `/strava` actuels en alias pour ne rien casser, puis migrer le front vers la
forme générique `/devices/{provider}`.

---

## 7. Sécurité, RGPD, robustesse (ne pas négliger)

Tout doit rester au niveau de sécurité existant (l'appli refuse déjà de démarrer en prod sans
secrets, chiffre les jetons, scope tout par club) :

- **Jetons chiffrés au repos** : déjà géré par `EncryptedStringConverter` sur `DeviceConnection`.
  Les jetons Garmin/Coros passent par le même chemin → rien à inventer.
- **Vérification des webhooks** : `verify_token` (Strava), signature/secret partagé (Garmin/Coros).
  Rejeter tout appel non authentifié. Endpoints webhook **hors** filtre JWT mais **validés** par
  secret + mapping `providerAthleteId`.
- **Idempotence** : la dédup `(athlete, source, externalId)` protège déjà l'entrant ;
  `pushed_workouts UNIQUE(workout_id, provider)` protège le sortant.
- **Droit à l'oubli (CDC §RGPD)** : `DELETE /devices/{provider}` doit **révoquer le jeton côté
  provider** (endpoint de deauthorization) en plus de supprimer la `DeviceConnection`, et retirer les
  séances poussées (`deleteWorkout`).
- **Quotas API** : Garmin/Coros imposent des limites d'appel → le webhook (pas de polling) et la
  fenêtre glissante côté push limitent la casse. Prévoir backoff + `last_error`.
- **Rate-limit / CORS / CSP** : déjà en place, ajouter les callbacks providers à ce qui doit l'être.
- **Multi-instance** : les webhooks sont stateless (OK multi-pod), contrairement au SSE. Le scheduler
  de push doit être **mono-leader** si un jour plusieurs pods (verrou/`ShedLock`) — noté comme la
  dette « SSE mono-instance » ; même vigilance ici.

---

## 8. Séquencement & effort (proposition)

| Phase | Contenu | Dépendance externe | Effort indicatif |
|---|---|---|---|
| **0 — Démarches** | Demandes d'accès **Garmin** (Health+Activity+Training) & **Coros** (Open API) | — (à lancer **jour 1**) | 1 j de dossier, puis **semaines** d'attente |
| **A — Strava temps réel** | Webhook Strava + signer le `state` OAuth + garder scheduler en filet | aucune | ~2–4 j |
| **B1 — Refactor port** | Extraire `ActivityProvider`, déplacer Strava derrière l'interface, endpoints `/devices/{provider}` | aucune | ~3–5 j |
| **B2 — Entrant Garmin** | Client + OAuth + webhook `/garmin` + mapping | accès Garmin obtenu | ~5–8 j |
| **B3 — Entrant Coros** | Client + OAuth + webhook `/coros` + mapping | accès Coros obtenu | ~4–6 j |
| **C1 — Modèle sortant** | Migration `pushed_workouts` + scopes push + `WorkoutMapper` + réconciliation | aucune (codable en amont) | ~4–6 j |
| **C2 — Push Garmin** | `WorkoutPushProvider` Garmin (create + schedule + delete) + UI portail | Training API obtenue | ~6–10 j |
| **C3 — Push Coros** | idem Coros | Open API push obtenue | ~5–8 j |
| **D — Finitions** | Réglages athlète, états UI « envoyé/à jour/erreur », force en musculation (option), FIT detail (option) | — | ~4–6 j |

> Les phases **A / B1 / C1** ne dépendent d'aucune validation externe → à faire **pendant** l'attente
> des accès. Quand Garmin/Coros répondent, B2/B3/C2/C3 s'enchaînent vite car les ports sont prêts.

---

## 9. Checklist « je démarre aujourd'hui »

- [ ] **Déposer la demande Garmin Developer** (Health + Activity + **Training API**) — chemin critique.
- [ ] **Déposer la demande partenaire Coros Open API** — chemin critique.
- [ ] Créer/vérifier l'**app Strava** et activer la **Webhook Events API** (self-service).
- [ ] Renseigner les redirect/callback URIs de prod (Railway) et de staging pour les 3 providers.
- [ ] **Lot A** : implémenter le webhook Strava (`/api/webhooks/strava`) + signer le `state` OAuth.
- [ ] **B1** : extraire l'interface `ActivityProvider`, déplacer le code Strava derrière, exposer
      `/devices/{provider}`, généraliser l'écran « Connexion montre » (3 boutons).
- [ ] **C1** : migration Liquibase `pushed_workouts` + colonnes `scopes`/`can_push` + `WorkoutMapper`
      (Workout/WorkoutStep + `calculatedPaces` → format cible) contre la doc Garmin/Coros.
- [ ] Écrire les tests (moteurs de mapping **purs** + MockMvc webhooks), style existant, sur H2/PG.
- [ ] Documenter les nouvelles variables d'env (déjà réservées) dans `docs/DEPLOIEMENT.md`.

---

## 10. Références internes

- Import & dédup existants : `service/StravaService.java`, `entity/Activity.java`
- Client à cloner par provider : `integration/StravaClient.java`
- Scheduler à jumeler (entrant + futur sortant) : `scheduler/StravaSyncScheduler.java`
- Connexion chiffrée (générique) : `entity/DeviceConnection.java`
- Séance source du push : `entity/Workout.java`, `entity/WorkoutStep.java` (+ `calculatedPaces`)
- Variables réservées : `.env.example` (`GARMIN_*`, `COROS_*`, `STRAVA_WEBHOOK_VERIFY_TOKEN`, `STRAVA_SYNC_CRON`)
- Dettes liées à traiter au passage : `docs/AUDIT-BETA-2026-06.md` (state OAuth à signer, FIT non géré,
  webhook Strava à venir, SSE/scheduler mono-instance)
- Cadre produit : `docs/Cahier-des-charges.md` (§ intégrations, Garmin/Coros/Polar), `docs/Design.md`
  (§ « Sync en cours »), `docs/Darilab/DARI Lab Cahier des Charges.md` (§ « pousser une séance vers la montre »)
