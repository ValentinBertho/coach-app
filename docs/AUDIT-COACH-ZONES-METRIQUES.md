# Audit ergonomique & proposition technique — Planificateur Coach (Axe A ergonomie, Axe B zones/métriques)

> Référence à égaler puis dépasser : **Nolio**.
> **Itération = audit + plan + proposition technique. Aucun code avant validation.**
> Stack : Angular standalone + signals + OnPush + CDK DragDrop (front) ; Spring/JPA + Liquibase (back).

> ✅ **Statut d'implémentation (Axe B — chantier zones/métriques) : Z1→Z4 LIVRÉS.**
> - **Z1** — `MetricType`/`TrainingZone`/`ZoneMetric` + CRUD + seed standard par club (lazy) + migrations 044-045 + écran club `/app/training-zones`.
> - **Z2** — `AthleteZoneValue` + `ZoneValueSyncService` (pré-remplissage physio, respecte MANUAL/verrou) + migration 046 + écran fiche athlète `/app/athletes/:id/zones`.
> - **Z3** — prescription par zone en lecture directe (`SessionCalculatorEngine.calculateFromZone`) + éditeur épuré (type · volume · zone) ; champs legacy conservés en lecture (aucune migration DDL, structure en JSON).
> - **Z4** — `PrescriptionZoneMapper` : migration douce des modèles legacy → zone à la lecture (réversible) ; non-régression du calcul/snapshots figés.
> Vérifs : back **165** tests / H2, front build + 4/4. Le moteur historique reste le **socle de pré-remplissage** (jamais supprimé).
>
> ✅ **Résidus Axe A traités :** **QA1** — arbre de catégories unifié (course/prépa/éducatifs) via `CategoryDomain` (migrations 047-048, enums legacy conservés) + filtres/badges/assignation dans prépa physique et éducatifs. **QA2** — page `/library` réutilisant `<app-session-library-panel>`.

> ⚠️ **État du dépôt au moment de cet audit.** L'Axe A (navigation, catégories, panneau gauche, drag & drop)
> a **déjà été largement implémenté** lors des phases 1→4 (voir `docs/AUDIT-COACH-PLANIFICATEUR.md`). Cet
> audit décrit donc l'Axe A **dans son état actuel** (post-implémentation) et se concentre sur l'**Axe B**
> (zones/métriques paramétrables + éditeur simplifié), qui est neuf et structurant.

---

## PARTIE 1 — Rapport d'audit

## Axe A — Ergonomie & navigation (état actuel, post-phases 1→4)

### A1. Navigation — ✅ traité

| Élément | Avant | Maintenant (`fichier:ligne`) |
|---------|-------|------------------------------|
| Nav desktop | 10 entrées à plat | **4 sections** COACHING / BIBLIOTHÈQUES / CLUB / RÉGLAGES (`coach-layout.component.html:20-40`) |
| Nav mobile | 3 items, 6 pages inaccessibles | bottom-nav 4 slots + sheet **« Plus »** complet (`coach-layout.component.html:74-113`) |
| Libellé ambigu | « Bibliothèque » | renommé **« Séances course »** (`coach-layout.component.html:31`) |

**Reste (basse priorité)** : le rail replié affiche les libellés de section en séparateurs — OK ; aucune friction bloquante.

### A2. Catégories personnalisées — ✅ partiel (course), à étendre

- Entité `SessionCategory` (club-scopée, hiérarchique) + CRUD `/clubs/{clubId}/session-categories` **déjà en place** côté back ; le front l'expose désormais (`template-list.component.ts:33-40`, service `session-category.service.ts`).
- **Course** : catégorisable (filtre, badge, assignation inline, gestion CRUD dans la bibliothèque).
- **Reste (chantier)** : **prépa physique** (`ExerciseCategory`) et **éducatifs** (`RunDrillCategory`) gardent leur enum propre → pas encore dans le même arbre `Category`. Voir A2-résiduel dans le plan.

### A3. Panneau gauche catégorisé — ✅ traité (calendrier)

- Composant réutilisable **`<app-session-library-panel>`** (`shared/components/session-library-panel/`) : accordéons **par catégorie** (course), groupes force/éducatifs, recherche instantanée, compteurs, repli mémorisé, items draggables.
- Branché dans le calendrier à gauche, ouvert par défaut sur desktop (`calendar.component.ts` `sidebarOpen`).
- **Reste** : page `/library` dédiée réutilisant le composant ; unification complète des 3 bibliothèques (dépend de A2-résiduel).

### A4. Drag & drop — ✅ traité

Couvert : glisser biblio→jour, déplacer jour→jour (optimiste+rollback), **réordonner intra-jour** (`orderIndex` + `PATCH /workouts/reorder`), **dupliquer au glisser (Alt/Ctrl)** (`POST /workouts/{id}/copy`), **réordonner les blocs** dans l'éditeur course, **menu contextuel clic droit**, **raccourcis clavier**, feedback (placeholder/preview/drop-zones).
**Reste (basse priorité)** : glisser une **semaine/bloc méso** entier.

> **Conclusion Axe A** : l'essentiel est livré et testé (build front + back verts, 151 tests back, 4 tests front). Les résidus (unifier prépa/éducatifs, page `/library`, drag de semaine) sont mineurs et listés dans le plan.

---

## Axe B — Système de zones/métriques paramétrable par le coach

C'est le cœur de cette itération. Aujourd'hui les allures sont **entièrement auto-calculées** ; le coach ne peut ni définir ses zones, ni saisir/verrouiller des valeurs. Objectif : lui **rendre la main**, façon Nolio, en conservant l'auto-calcul comme **socle de pré-remplissage**.

### B1. Audit du système de calcul actuel

**Chaîne complète (de la prescription à l'allure/FC affichée) :**

```
Éditeur (front)                     Back : calcul                          Athlète (source de vérité)
─────────────────                   ───────────────                        ─────────────────────────
CourseBlock                         SessionCalculatorService                Athlete
  type                                .contextFor()  ───────────────────►    lt1Ms/lt2Ms/vcMs (m/s)
  reps × distance|durée               assemble AthletePaceContext            fcLt1/fcLt2/hrMax
  prescription:                              │                               fcDomain1/2Pct, vdot
    ref (PrescriptionRef)                    ▼                             AthleteVdotPace
    minPct / maxPct           SessionCalculatorEngine.calculate()            pace800m…paceMarathon (s)
                                resolveBasePace(ref) → base                         │
                                paceFast = base / (maxPct/100)  ◄─────── % appliqué │
                                paceSlow = base / (minPct/100)                      │
                                FC = interp. linéaire LT1↔LT2                        │
                                RPE = domaine d'intensité
                                → CalculatedBlockResponse (allure/vitesse/FC/RPE/durée/distance)
```

**Références clés :**

| Élément | `fichier:ligne` |
|---------|-----------------|
| Enum **figé** des référentiels (11 : `PCT_LT1/LT2/VC` + 8 `PCT_PACE_*`) | `entity/enums/PrescriptionRef.java:8-20` |
| Enum **figé** `IntensityZone` Z1–Z5 (legacy : porté par `WorkoutStep.zone`, **pas** par le chemin course DARI Lab) | `entity/enums/IntensityZone.java:4-10` |
| Prescription = `ref + minPct + maxPct` (jamais de valeur sèche) | `dto/session/CoursePrescription.java:9-13` |
| Bloc → prescription | `dto/session/CourseBlock.java:11-19` |
| Cœur du calcul (base × %) | `engine/SessionCalculatorEngine.java:62-106` |
| Résolution base pace (mesuré → repli VDOT) | `engine/SessionCalculatorEngine.java:120-143` |
| FC par interpolation LT1↔LT2 | `engine/SessionCalculatorEngine.java:160-172` |
| Assemblage du profil athlète | `service/SessionCalculatorService.java:120-140` |
| Contrainte de saisie % : `30 ≤ minPct ≤ maxPct ≤ 150` | `dto/request/SessionCalcRequest.java:18-26` |
| Source de vérité physio | `entity/Athlete.java:97-156`, `entity/AthleteVdotPace.java:32-57` |
| **Snapshot figé** à la planification (structure ref+% **et** cibles calculées) | `service/CourseSessionService.java:77-90` |

**Modèle « référentiel + fourchette % » — limites :**

| # | Limite (`fichier:ligne`) | Impact | Gravité |
|---|--------------------------|--------|---------|
| B1.1 | Référentiels **figés** (enum de 11) — impossible d'ajouter une zone/métrique (`PrescriptionRef.java:8-20`) | Le coach est enfermé dans le vocabulaire physio de l'app | Haute |
| B1.2 | **Aucune saisie manuelle** : la cible est toujours `base × %`, jamais une valeur entrée par le coach (`SessionCalculatorEngine.java:62-106`) | Le coach ne peut pas imposer « 4:05/km » ni corriger une estimation | Haute |
| B1.3 | Zones d'intensité **figées** Z1–Z5, non nommables, non paramétrables (`IntensityZone.java:4-10`) | Pas de zones « maison » (VMA courte, Fartlek…) | Haute |
| B1.4 | Couplage fort **VDOT/seuils** : sans profil, `computable=false`, le bloc n'affiche rien (`SessionCalculatorEngine.java:64-66`) | Athlète débutant sans test → séance « vide » de cibles | Moyenne |
| B1.5 | Double saisie **référentiel + min% + max%** par bloc, jargon `PCT_*` (`session-editor.component.html:90-96`) | Éditeur lourd, cognitif (voir B4) | Haute |
| B1.6 | Valeurs **non persistées par athlète** : recalculées à la volée, pas éditables ni verrouillables (`SessionCalculatorService.java:120-140`) | Impossible de figer/ajuster les zones d'un athlète façon Nolio | Haute |

**Ce qui devra bouger** (cartographie d'impact) : `PrescriptionRef` (enum), `CoursePrescription` (DTO), `CourseBlock`, `SessionCalcRequest`, `SessionCalculatorEngine` + `Service`, snapshots figés (`session_snapshot`/`calculated_paces` sur `Workout`), front (`session-editor`, `course.model.ts`, `course-prescription-view`, `range-prescription-pill`).

### B4. Audit de l'éditeur de séance (friction de saisie)

Par bloc, l'éditeur impose **7 contrôles** (`session-editor.component.html:77-96`) :

```
[ type ▾ ] [ ×N reps ] [ mesure: distance|durée ▾ ] [ 400 m / 8 min ]
[ référentiel: % allure 5 km ▾ ] [ min % ] [ max % ]  [✕]
```

+ recalcul serveur à chaque frappe (`session-editor.component.ts:322-329`), + presets (`:190-208`), + calc preview.

| # | Friction (`fichier:ligne`) | Impact | Gravité |
|---|----------------------------|--------|---------|
| B4.1 | 7 contrôles/bloc, dont **3** dédiés à l'intensité (ref + min% + max%) (`session-editor.component.html:90-96`) | Saisie lente, charge cognitive | Haute |
| B4.2 | Jargon `PCT_LT1`, `% allure 5 km` exposé au coach (`session-editor.component.ts:85-93`) | Correspondance au monde réel faible (Nielsen) | Moyenne |
| B4.3 | Choix **distance vs durée** via un select + un champ, pas une bascule | Un clic de trop, redondant | Basse |
| B4.4 | Aucune **zone pré-sélectionnée** selon le type de bloc | Chaque bloc repart de zéro | Moyenne |
| B4.5 | Si profil incomplet → cibles vides sans action directe depuis le bloc | Cul-de-sac (l'action de bootstrap est ailleurs, `:301-320`) | Moyenne |

---

## PARTIE 2 — Comparatif Nolio

| Capacité | Nolio | App actuelle | Cible |
|----------|-------|--------------|-------|
| Zones de travail nommées par le coach | ✅ nombre libre | ❌ Z1–Z5 figées / 11 `PrescriptionRef` | ✅ `TrainingZone` (n libre) |
| Métriques par zone (allure, FC, LT1/LT2, puissance…) | ✅ multiples | ⚠️ calculées, non éditables | ✅ `MetricType` extensible |
| Valeurs **par athlète** sur la fiche | ✅ | ❌ recalcul volatil | ✅ `AthleteZoneValue` |
| Saisie/ajustement manuel + verrouillage | ✅ | ❌ | ✅ source AUTO/MANUAL + lock |
| Pré-remplissage auto (physio) | ✅ | ✅ (mais **seul** chemin) | ✅ **socle** puis éditable |
| Prescription = zone à 100 %, sans % | ✅ | ❌ (ref + min%/max%) | ✅ `prescription.zoneId` |
| Athlète sans données physio | dégradé propre | ❌ cibles vides | ✅ zone « à renseigner » + CTA |
| Éditeur de séance épuré | ✅ | ❌ 7 contrôles/bloc | ✅ 3 (type/volume/zone) |
| Nav groupée / panneau gauche / DnD | ✅ | ✅ (phases 1→4) | ✅ maintenu |

---

## PARTIE 3 — Modèle cible (proposition technique)

### 3.1 Schéma d'entités & relations

```
┌──────────────┐        ┌──────────────────┐        ┌──────────────┐
│  MetricType  │        │   TrainingZone   │        │   Athlete    │
│──────────────│        │──────────────────│        │──────────────│
│ id           │        │ id               │        │ id           │
│ club_id? (n) │        │ club_id          │        │ …physio…     │
│ code (PACE…) │        │ name             │        └──────┬───────┘
│ name         │        │ color            │               │
│ unit         │        │ sort_order       │               │
│ format       │        │ scope COACH|CLUB │               │
│ direction    │        │ discipline? (n)  │               │
│ is_builtin   │        │ is_builtin       │               │
└──────┬───────┘        └───┬────────┬─────┘               │
       │                    │        │                     │
       │   ┌────────────────┘        │                     │
       │   │   zone_metric (config : quelles métriques une zone porte)
       │   │   ┌───────────────┐                           │
       │   └──►│  ZoneMetric   │◄──┐                        │
       │       │ zone_id       │   │ metric_type_id         │
       │       └───────────────┘   │                        │
       │                           │                        │
       ▼                           ▼                        ▼
┌───────────────────────────────────────────────────────────────┐
│                     AthleteZoneValue                            │
│───────────────────────────────────────────────────────────────│
│ id · athlete_id · zone_id · metric_type_id                     │
│ value_min · value_max (n)   ← min/max ou valeur unique         │
│ source AUTO|MANUAL · locked · updated_at                       │
│ UNIQUE(athlete_id, zone_id, metric_type_id)                    │
└───────────────────────────────────────────────────────────────┘

Prescription d'un bloc :
CourseBlock.prescription = { zoneId }   ← 100 % de la zone (plus de ref/min/max %)
Cible affichée = AthleteZoneValue(athlete, zone, métrique) pour l'athlète courant.
```

**Entités (JPA) :**

- **`MetricType`** — catalogue extensible. `code` (`PACE`, `HR`, `SPEED`, `PCT_HRMAX`, `POWER`, `RPE`…), `unit` (`S_PER_KM`, `BPM`, `KMH`, `PCT`, `W`), `format` (`MMSS`, `INT`, `DEC1`), `direction` (`HIGHER_HARDER`|`LOWER_HARDER`, ex. allure = lower_harder), `club_id` nullable (null = catalogue global seedé), `is_builtin`.
- **`TrainingZone`** — `club_id`, `name`, `color`, `sort_order`, `scope` (COACH/CLUB), `discipline` nullable, `is_builtin`. Nombre libre, ré-ordonnable (drag).
- **`ZoneMetric`** — jointure `zone_id × metric_type_id` : **quelles métriques** une zone porte (drive l'écran fiche athlète + l'affichage). Défaut au seed : `PACE` + `HR`.
- **`AthleteZoneValue`** — `athlete_id × zone_id × metric_type_id → value_min/value_max`, `source`, `locked`. **La fiche athlète porte les valeurs** (façon Nolio).

### 3.2 Migrations (Liquibase, additives et réversibles)

```
044-metric-types            createTable metric_types (+ seed catalogue standard)
045-training-zones          createTable training_zones + zone_metric (+ seed jeu de zones standard/club)
046-athlete-zone-values     createTable athlete_zone_values (UNIQUE athlete,zone,metric) + FKs
047-block-zone-ref          CourseBlock.prescription : ajout zoneId (JSON, non destructif) ;
                            colonnes existantes ref/min/max conservées pour les snapshots figés.
```

Chaque changeset a un `rollback`. Le seed du **jeu de zones standard** (façon Daniels/physiologique) évite la page blanche : Récupération · Endurance fondamentale · Marathon (seuil aérobie) · Seuil (tempo) · VO2 (intervalles) · Anaérobie/Sprint — modifiables.

### 3.3 Endpoints (CRUD)

```
# Zones (club)
GET    /clubs/{clubId}/training-zones
POST   /clubs/{clubId}/training-zones
PUT    /clubs/{clubId}/training-zones/{id}
DELETE /clubs/{clubId}/training-zones/{id}
PATCH  /clubs/{clubId}/training-zones/reorder          (sort_order)
PUT    /clubs/{clubId}/training-zones/{id}/metrics     (ZoneMetric : métriques portées)

# Métriques (catalogue)
GET    /clubs/{clubId}/metric-types
POST   /clubs/{clubId}/metric-types                    (métrique custom)

# Valeurs par athlète (fiche athlète)
GET    /clubs/{clubId}/athletes/{athleteId}/zone-values
PUT    /clubs/{clubId}/athletes/{athleteId}/zone-values/{zoneId}/{metricId}   (saisie/ajustement + lock)
POST   /clubs/{clubId}/athletes/{athleteId}/zone-values/resync                (regénère les AUTO non lockés)
```

### 3.4 DTO impactés

- **Back** : `CoursePrescription` → `{ UUID zoneId }` (+ champs legacy `ref/minPct/maxPct` conservés en lecture) ; `SessionCalcRequest` → `{ zoneId, reps, distanceM, durationS }` ; `CalculatedBlockResponse` inchangé en surface (allure/FC/…) mais **alimenté par lecture directe** des `AthleteZoneValue` ; nouveaux : `TrainingZoneResponse/Request`, `MetricTypeResponse`, `AthleteZoneValueResponse/Request`.
- **Front** : `course.model.ts` `CoursePrescription` → `{ zoneId }` ; nouveaux modèles `training-zone.model.ts`, `metric-type.model.ts`, `athlete-zone-value.model.ts` ; services associés.

### 3.5 Rôle résiduel du moteur (essentiel)

`SessionCalculatorEngine` **ne calcule plus la cible à la volée** pour la prescription. Il devient un **générateur de valeurs par défaut** :

- Nouveau `ZoneValueSyncService.resync(athlete)` : à partir du profil (`Athlete` + `AthleteVdotPace`), il **remplit les `AthleteZoneValue` de source AUTO non verrouillées** :
  - *Endurance fondamentale* → allure ← easy VDOT, FC ← `fcDomain1Pct·fcMax` ;
  - *Seuil* → allure ← `lt2Ms` (ou repli VDOT 10 km), FC ← `fcLt2` ;
  - *VO2* → allure ← `pace3000/5km`, FC ← proche `fcMax` ; etc. (table de correspondance versionnée).
- La **lecture** de la cible en prescription/affichage devient une **lecture directe** de `AthleteZoneValue` (plus de `base × %`). Le moteur historique (`VdotEngine`, `LactateThresholdEngine`, `CriticalSpeedEngine`) reste le **socle de pré-remplissage** — conforme à la contrainte.

### 3.6 Migration & compatibilité (B3)

| Sujet | Décision recommandée |
|-------|----------------------|
| **Snapshots déjà planifiés** (`session_snapshot`/`calculated_paces`) | **Immuables** : ce sont des figés historiques. On garde le **chemin de lecture legacy** (ref+% déjà calculés) pour les afficher tels quels. Aucune réécriture. |
| **Modèles de bibliothèque** (templates course) | **Migration douce** : un script mappe chaque `PrescriptionRef` (+ bande % courante) vers la **zone standard la plus proche** → `prescription.zoneId`. Réversible. |
| Coexistence % ↔ zones | **Cutover au niveau *authoring*** (l'éditeur ne propose plus que des zones) **+ coexistence en *lecture*** (snapshots legacy lus via l'ancien format). C'est le meilleur compromis : UX propre sans casser l'historique. |
| Jeu de zones standard | Seedé par club (`is_builtin`), modifiable — pas de page blanche. |
| Athlète sans physio | Zones présentes, `AthleteZoneValue` AUTO nulles → bloc affiche « zone à renseigner » + CTA vers la fiche ; **n'empêche pas** d'écrire la séance (le template référence la zone ; la cible reste « — » pour cet athlète tant que non renseignée). |

### 3.7 Éditeur simplifié — modèle cible (B4)

**Un bloc = type + volume + zone.** Plus de référentiel ni de %.

```
Avant (7 contrôles) :
[type ▾][×N][distance|durée ▾][valeur][référentiel PCT_* ▾][min %][max %]

Après (3 contrôles + cible auto) :
[type ▾]   [ ×N  ⇆  400 m / 8 min ]   [ Zone ▾ (nom + pastille couleur) ]
           └ bascule distance/durée      └ cible lue sur la fiche athlète :
                                            « 3:35–3:45/km · 168–174 bpm » (lecture seule)
```

- **Zone pré-sélectionnée** selon le type de bloc (intervals→VO2, tempo→Seuil, warmup→Endurance…).
- **Cible en lecture** : allure/FC affichées automatiquement depuis `AthleteZoneValue` (mode séance planifiée) ou depuis un athlète « aperçu » (mode modèle).
- **Zéro cul-de-sac** : zone non renseignée → chip « à renseigner » cliquable → fiche athlète.
- **Avancé replié** : possibilité de surcharger ponctuellement une valeur (rare) derrière un repli — non requis au parcours nominal.

---

## PARTIE 4 — Plan d'amélioration priorisé

### 4.1 Quick wins (faible effort / fort impact)

| # | Objectif | Front | Back | Effort | Dépend. | Risques |
|---|----------|-------|------|--------|---------|---------|
| QA1 | Unifier prépa/éducatifs sous `Category` (résidu A2) | filtres/badges dans strength + run-drills | `categoryId` sur `PpExercise`/`RunDrill` (colonnes déjà côté enum → FK nullable) | **M** | — | Migration douce |
| QA2 | Page `/library` réutilisant `<app-session-library-panel>` | route + montage | — | **S** | — | Faible |
| QA3 | Bascule distance/durée en un toggle (B4.3) | `session-editor` | — | **S** | — | Faible |
| QA4 | Zone pré-sélectionnée par type de bloc (B4.4) — après Z3 | presets | — | **S** | Z3 | Faible |

### 4.2 Chantiers structurants (Axe B)

| # | Objectif | Front | Back | Effort | Dépend. | Risques |
|---|----------|-------|------|--------|---------|---------|
| **Z1** | **Catalogue métriques + zones du coach** | écran « Zones » (club) : liste, création, couleur, ordre (drag), métriques portées | `MetricType`, `TrainingZone`, `ZoneMetric` + CRUD + **seed standard** (migrations 044-045) | **M** | — | Seed cohérent |
| **Z2** | **Valeurs par athlète + pré-remplissage** | écran **« Zones & métriques »** sur la fiche athlète (saisie, min/max, lock, resync) | `AthleteZoneValue` (046) + `ZoneValueSyncService` (seed depuis moteur) + endpoints | **L** | Z1 | Mapping physio→zones |
| **Z3** | **Prescription par zone (sans %) + éditeur épuré** | `session-editor` : sélecteur de zone, cible auto en lecture, 3 contrôles/bloc ; `course-prescription-view`/`range-prescription-pill` alimentés par valeurs | `CoursePrescription.zoneId` (047), `SessionCalcRequest` par zone, moteur en **lecture directe** | **M** | Z1, Z2 | Cutover authoring |
| **Z4** | **Migration templates + lecture legacy des snapshots** | — | script de mapping `PrescriptionRef`→zone ; chemin de lecture legacy conservé ; tests | **M** | Z3 | Non-régression snapshots |

### 4.3 Séquencement en phases

```
Phase A-résidus  [QA1, QA2]        M/S — indépendant, ferme l'Axe A.
Phase Z1         [Z1]              M   — socle données (métriques + zones + seed). Aucun impact éditeur.
Phase Z2         [Z2]              L   — valeurs par athlète + pré-remplissage depuis le moteur existant.
                                        La fiche athlète devient la source de vérité (façon Nolio).
Phase Z3         [Z3, QA3, QA4]    M   — bascule de l'éditeur vers « zone à 100 % », saisie épurée.
                                        Dépend de Z1+Z2 (il faut des zones et des valeurs à lire).
Phase Z4         [Z4]              M   — migration des modèles + lecture legacy des séances figées.
```

**Ordre justifié** : on installe d'abord le **référentiel de données** (Z1), puis les **valeurs par athlète + auto-remplissage** (Z2) — sans quoi l'éditeur n'aurait rien à lire —, ensuite seulement le **cutover UX de l'éditeur** (Z3), enfin la **migration/compat** des contenus existants (Z4). Chaque phase est livrable et non destructive ; l'auto-calcul reste le socle à chaque étape.

---

## PARTIE 5 — Wireframes (ASCII)

### 5.1 Fiche athlète — écran « Zones & métriques »

```
┌ Athlète : Marie D. ▸ Zones & métriques ───────────────────────────────┐
│  [ Resynchroniser depuis le profil ]      auto = 🔄   manuel = ✎ 🔒     │
├───────────────┬───────────────────────────┬───────────────────────────┤
│ Zone          │ Allure (/km)              │ FC (bpm)                  │
├───────────────┼───────────────────────────┼───────────────────────────┤
│ ● Récupération│ 5:40 – 6:10        🔄      │ 120 – 135        🔄        │
│ ● Endurance   │ 5:00 – 5:25        🔄      │ 135 – 150        🔄        │
│ ● Marathon    │ 4:20 – 4:30        ✎ 🔒    │ 155 – 162        🔄        │  ← valeur ajustée + verrouillée
│ ● Seuil       │ 4:00 – 4:08        🔄      │ 168 – 174        🔄        │
│ ● VO2         │ 3:35 – 3:45        🔄      │ 178 – 185        🔄        │
│ ● Sprint      │  —   (à renseigner)       │  —                        │  ← pas de donnée → CTA
└───────────────┴───────────────────────────┴───────────────────────────┘
   [+ Métrique]  (ajoute une colonne : Puissance W, %FCmax, LT1, LT2…)
```

### 5.2 Éditeur de séance simplifié (mode séance planifiée)

```
┌ Corps de séance ───────────────────────────────────────────────────────┐
│  + Intervalles VO2   + Seuil 20'   + Tempo 4 km   + Bloc vierge          │
├─────────────────────────────────────────────────────────────────────────┤
│ ⠿ [Intervalles ▾]   [ ×6  ⇆  1000 m ]   [ ● VO2 ▾ ]                      │
│      cible (Marie D.) : 3:35–3:45/km · 178–185 bpm            ✕          │
│ ⠿ [Récup       ▾]   [ ×5  ⇆   90 s  ]   [ ● Endurance ▾ ]                │
│      cible : 5:00–5:25/km · 135–150 bpm                        ✕          │
├─────────────────────────────────────────────────────────────────────────┤
│  Total estimé : 42 min · 9,2 km                                          │
└─────────────────────────────────────────────────────────────────────────┘
     3 contrôles/bloc (type · volume · zone) — la cible est lue, jamais saisie.
```

### 5.3 Parcours chronométré — « créer une séance de 3 blocs »

| Étape | Avant (modèle % ) | Après (zones) |
|-------|-------------------|---------------|
| Par bloc | type + reps + mesure + valeur + **ref + min% + max%** = 7 champs | type + volume + **zone** = 3 champs |
| 3 blocs | ~21 saisies + choix `PCT_*` | ~9 saisies, zéro % |
| Cibles | à interpréter (fourchette de %) | **affichées** (allure/FC réelles) |
| Décisions intensité | 3 (ref, min, max) × 3 blocs = 9 | 1 (zone) × 3 = 3 |

**Gain : ~55 % de saisies en moins et suppression totale du jargon `PCT_*`.**

---

## Contraintes respectées
- Stack : Angular standalone + signals + OnPush + CDK (front) ; Spring/JPA + Liquibase (back) ; tokens `var(--…)`, `shared/`.
- **Auto-calcul conservé** comme socle de pré-remplissage (`VdotEngine`, `LactateThresholdEngine`, `CriticalSpeedEngine`), jamais supprimé.
- Non-régression : snapshots figés immuables + lecture legacy ; migrations additives réversibles ; extensibilité (ajouter une zone/métrique = données, pas de refonte).
- Accessibilité & mobile : écran zones responsive, sélecteur de zone au clavier, cible en lecture annoncée (aria).

---

*Prochaine étape : validation du plan avant toute implémentation. Démarrage recommandé : Phase Z1 (catalogue métriques + zones + seed standard), sans impact sur l'éditeur existant.*
