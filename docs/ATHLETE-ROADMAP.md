# Darilab — Données visibles par l'athlète : audit & plan d'évolution

Audit du périmètre **actuel** du portail athlète (PWA, endpoints `/me`) et
**plan d'évolution** pour une application de suivi plus complète, sans jamais
violer les invariants (l'athlète **lit**, **déplace** une séance, **renseigne**
son retour — mais ne modifie/supprime jamais le contenu).

---

## 1. Ce que l'athlète voit aujourd'hui

Endpoints exposés sous `/me` (cf. `AthletePortalController`) et écrans associés :

| Donnée | Endpoint `/me` | Écran |
|---|---|---|
| Séance du jour (course + force) + prescription | `today`, `pp/scheduled`, `*/prescription` | **Aujourd'hui** |
| Calendrier (séances course + force + indispos) | `workouts?from&to`, `pp/scheduled`, `unavailabilities` | **Mon calendrier** (agenda) |
| Déplacer une séance (date) | `workouts/{id}/move`, `pp/scheduled/{id}/move` | Agenda (bottom sheet) |
| Retour de séance (RPE/fatigue/douleur/commentaire) | `workouts/{id}/feedback`, `pp/scheduled/{id}/feedback` | Aujourd'hui (check 10 s) |
| Saisie des séries de force + e1RM | `pp/scheduled/{id}/results` | Aujourd'hui |
| Progression e1RM par exercice + historique | `pp/1rm`, `pp/1rm/{id}/history` | **Mes progrès** |
| Suggestion de progression (coach) | `pp/scheduled/{id}/progression` | Aujourd'hui |
| Prochaine course | `next-race` | Aujourd'hui |
| Messagerie temps réel | `messages*` | **Messages** |
| Profil / RGPD (export, suppression) | `GET /me`, `export`, `DELETE` | **Profil** |

**Constat** : le portail est **orienté exécution** (faire la séance du jour, la
déplacer, donner son retour, voir sa force). Il manque la dimension
**« se connaître / suivre ses tendances »**.

---

## 2. Ce que l'athlète NE voit PAS (alors que la donnée existe déjà en base)

Ces données sont calculées/stockées et exposées **côté coach uniquement**
(`/clubs/{clubId}/athletes/{athleteId}/...`). Aucune lecture côté `/me` :

| Donnée manquante (existe côté coach) | Source backend | Valeur pour l'athlète |
|---|---|---|
| **Profil physio** : VDOT, LT1/LT2, VC, domaines d'intensité | `…/physio`, `…/vdot` | comprendre ses seuils |
| **Allures d'entraînement** (table VDOT) | `…/vdot` (paces) | savoir à quelle allure courir |
| **Charge d'entraînement** : ACWR, ATL/CTL, monotonie, répartition par domaine | `…/load` (`LoadEngine`) | piloter sa fatigue / éviter la blessure |
| **Charge mécanique vs métabolique** (force, UA) | `StrengthLoadTracking` | équilibre structurel/énergétique |
| **Analytics** : volume hebdo prévu/réalisé, distribution de zones, adhérence | `…/analytics` | voir sa régularité et sa progression |
| **Historique des séances passées** + mon ressenti dans le temps | `workouts` (passées) | revivre/analyser ses séances |
| **Activités importées** (Strava/GPX) + carte | `…/activities` | retrouver ses sorties réelles |
| **Performances / records** (chronos par distance) | `…/performances` | suivre ses PR |
| **Tests lactate** (historique + courbe) | `…/tests` (lactate) | comprendre ses tests labo |
| **Objectifs** (liste complète, pas seulement le prochain) | `objectives` | vue d'ensemble de sa saison |

> Techniquement, la plupart de ces évolutions = **exposer un endpoint `/me/*`
> en lecture seule** qui réutilise les services existants en mode athlète-scopé
> (pattern déjà éprouvé : `athleteCalendar`, `my1rm`, `historyForAthlete`), + un
> écran de lecture. Pas de nouveau moteur de calcul.

---

## 3. Plan d'évolution (par phases, par valeur)

### Phase 1 — « Me connaître » (fort impact, faible effort : données déjà calculées)
1. **Mon profil physio** (lecture) : VDOT, LT1/LT2, VC, domaines + **mes allures d'entraînement**.
   - Back : `GET /me/physio`, `GET /me/vdot` (athlète-scopé).
   - Front : onglet **Profil** enrichi (badges physio + table d'allures).
2. **Ma charge** : ACWR (avec bande de sécurité), ATL/CTL, monotonie, répartition de zones.
   - Back : `GET /me/load`.
   - Front : section dans **Mes progrès** (réutilise `AcwrIndicator`, `MetricCard`).
3. **Mes objectifs** (liste complète, lecture) : `GET /me/races` → bloc dans Aujourd'hui/Profil.

### Phase 2 — « Mon histoire » (engagement, régularité)
4. **Mon historique de séances** : séances passées (course + force) avec mon ressenti
   (RPE/fatigue/douleur) et le réalisé vs prescrit. Filtres par période/type.
   - Back : `workouts` accepte déjà une plage → ajouter une vue « passées » + récap feedback.
   - Front : nouvel écran **Historique** (ou onglet dans Mes progrès).
5. **Mes analytics** : volume hebdo prévu/réalisé, distribution de zones, **adhérence**.
   - Back : `GET /me/analytics`.
   - Front : réutiliser les graphes de l'écran coach (barres + zones + KPI).
6. **Mes activités** (Strava/GPX) : liste + détail + **carte Leaflet** (déjà dans la stack).
   - Back : `GET /me/activities`, `GET /me/activities/{id}`.
   - Front : onglet **Activités** (mobile).
7. **Mes performances / records** : PR par distance, évolution.
   - Back : `GET /me/performances`. Front : bloc dans Mes progrès.

### Phase 3 — « Aller plus loin » (différenciation)
8. **Mes tests lactate** (lecture) : historique + courbe de profil (axe allure inversé).
9. **Charge mécanique/métabolique** (force) : double-barres + ratio.
10. **Notifications & rappels** : séance du jour, message coach, alerte charge — + **badges/streaks**
    de régularité (gamification douce).
11. **Lecture détaillée d'une séance future** (intention + blocs + fourchettes) depuis le calendrier
    (l'endpoint `prescription` existe déjà ; il manque l'écran de détail athlète).

---

## 4. Invariants à respecter dans toutes ces évolutions

- **Lecture seule** : aucune de ces vues ne permet de modifier la prescription.
- **Multi-tenant** : l'athlète ne voit **que** ses propres données (`/me`, scopé par le token).
- **Données calculées** clairement étiquetées (origine `calculé` / `mesuré`) — réutiliser
  `DataOriginTag`.
- **État de forme ≠ RPE** (fatigue + douleur), **prescriptions en fourchettes** : déjà gérés
  par les primitives existantes (`ReadinessGauge`, `RangePrescriptionPill`).
- **Offline-friendly** : les écrans de lecture clés (profil, charge, prochaine séance) doivent
  rester consultables hors-ligne (cache PWA).

---

## 5. Estimation rapide

| Phase | Endpoints `/me` à ajouter | Écrans front | Effort relatif |
|---|---|---|---|
| 1 | `physio`, `vdot`, `load`, `races` | Profil enrichi + section charge | ●●○ |
| 2 | `analytics`, `activities`, `performances` (+ vue historique) | Historique, Activités, Analytics | ●●● |
| 3 | `tests` (lactate), `strength-load`, notifications | Tests, charge force, rappels | ●●● |

> Recommandation : démarrer par la **Phase 1** — elle transforme déjà le portail
> « exécutant » en outil de **suivi personnel** avec un effort modéré (les moteurs
> et la donnée existent ; on expose et on affiche).
