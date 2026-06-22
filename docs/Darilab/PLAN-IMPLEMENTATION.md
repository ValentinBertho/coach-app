# DARI Lab Training — Plan d'implémentation sur socle Angular / Java / Spring

> **Objet** : adapter le cahier des charges fonctionnel DARI Lab (dossier `docs/Darilab/`)
> à l'application existante **CoachRun** (Spring Boot 3.2.5 / Java 21 / PostgreSQL / Liquibase
> / Angular 17 PWA), **sans reprendre l'architecture technique du doc** (Next.js / Supabase).
> Ce document est le plan de construction. Il prime sur la « Stack technique » (§2) et l'« API
> Route Handlers » (§13) du cahier des charges, qui sont remplacées par leurs équivalents Spring/Angular.
>
> Sources analysées : `DARI Lab Cahier des Charges.md`, `DARI Lab Training Architecture.md`
> (43 tables, ~20 ENUMs, moteurs de calcul, parcours, wireframes ASCII), `dari-lab-wireframes.html`
> (18 écrans : `s-dashboard`, `s-athletes`, `s-athlete-detail`, `s-calendar`, `s-data`,
> `s-session-editor`, `s-library`, `s-exercises`, `s-strength`, `s-tests`, `s-lactate`,
> `s-analysis`, `s-messages-coach`, `s-sync`, `s-club`, `s-settings`, `s-notifications`, `s-mobile`),
> `dari-types.ts`, `DARI Lab API et RLS.md`.

---

## 0. Décisions structurantes (à valider)

| # | Décision | Recommandation retenue dans ce plan |
|---|----------|-------------------------------------|
| D1 | Évoluer le modèle existant **en place** vs créer un modèle DARILAB **parallèle** | **Évolution en place** : on réutilise/étend `Athlete`, `Club`, `Workout`, `WorkoutTemplate`, `TrainingGroup`, `Activity`, `Message`, `RaceObjective`. On ajoute les domaines manquants (physio, force, permissions). On évite un « big bang ». |
| D2 | Équivalent du **RLS Supabase** | **Autorisation côté service** Spring, en étendant le `ClubAccessValidator` existant vers un `AthleteAccessValidator` (référent + permissions graduées + privé/club). Pas de RLS Postgres natif. |
| D3 | Équivalent **Supabase Realtime** (messagerie, notifications) | **SSE** (Server-Sent Events) Spring d'abord (déjà du polling/push web en place), WebSocket STOMP si besoin de bidirectionnel riche. |
| D4 | Équivalent **Supabase Storage** (images exos, PDF, pièces jointes) | **S3-compatible** (ou stockage disque en dev) derrière un `StorageService` ; URLs signées. |
| D5 | **App mobile athlète** (React Native dans le doc) | On capitalise sur la **PWA Angular** existante (portail athlète `today`/`profile` déjà là). React Native repoussé hors périmètre initial. |
| D6 | **Calculs moteurs** (VDOT, lactate, 1RM, charge) | Implémentés en **Java** côté back (services `engine/`) — source de vérité — avec miroir TypeScript léger côté front pour l'aperçu temps réel des éditeurs. |

> Les sprints ci-dessous supposent D1–D6. Si on préfère un modèle parallèle (D1 = parallèle),
> seul le §4 (mapping) change ; la roadmap reste valable.

---

## 1. Cartographie macro : socle existant → cible DARILAB

| Domaine DARILAB | Existant CoachRun | Stratégie |
|-----------------|-------------------|-----------|
| Identité & rôles (`coach`/`athlete`/`admin`) | `User` (PLATFORM_ADMIN/HEAD_COACH/COACH/ATHLETE), JWT | **Réutiliser**. Mapper `owner≈HEAD_COACH`, `coach_principal`/`coach_assistant` via rôle club. |
| Club multi-coach + permissions graduées | `Club` (tenant), `ClubAccessValidator`, M2M `athlete_coaches` | **Étendre** : ajouter `club_member` (rôle club) + `athlete_coach_permission` (read/comment/write, expires_at) + privé/club. |
| Profil athlète physio (LT1/LT2/VC/VDOT/domaines) | `Athlete` (hrMax, hrRest, vma, weight, chiffré) | **Étendre** `Athlete` ou table fille `athlete_physio_profile`. |
| Performances → VDOT | — (objectifs course via `RaceObjective`) | **Nouveau** : `athlete_performance` + `athlete_vdot_pace` + moteur VDOT. |
| Bibliothèque séances course (arbre 3 niveaux) | `WorkoutTemplate` + (catégories à ajouter) | **Étendre** : `session_category` hiérarchique + `WorkoutTemplate` enrichi (prescription en fourchettes JSONB). |
| Séance course planifiée (snapshot, retour) | `Workout` + `WorkoutStep` + `rpe`/`athleteComment` | **Étendre** : ajouter `session_snapshot`, `calculated_paces`, `fatigue`, `pain`, `moved_by_athlete`. |
| Tests lactate + seuils | — | **Nouveau** : `lactate_test` + `lactate_test_step` + moteur LT1/LT2 (Dmax modifié). |
| Domaines d'intensité 1/2/3 | `IntensityZone` (enum) | **Remplacer/compléter** par classification physiologique configurable. |
| Charge ACWR (course + force unifiée) | `AnalyticsService` (zones, charge) | **Étendre** : `training_load_cache` (ATL/CTL/ratio/monotonie) + sRPE. |
| Module Préparation Physique (force) | — (placeholder `WorkoutType.STRENGTH`) | **Nouveau domaine complet** (cf. §3.D). |
| Groupes d'athlètes personnalisés | `TrainingGroup` | **Étendre** vers groupes colorés M2M (un athlète ∈ 0..n groupes). |
| Objectifs A/B/C calendrier | `RaceObjective` (priorité, statut) | **Réutiliser/mapper** sur `objective_type` A/B/C. |
| Sync Strava/Garmin | `Activity` (GPX/TCX import, parser) | **Étendre** : `sync_token` (OAuth) + `synced_activity` + webhooks. |
| Messagerie temps réel + notifications | `Message`, `PushSubscription`, push web | **Étendre** : conversations, pièces jointes, SSE temps réel, `notification`. |
| Calendrier drag & drop multi-types | `calendar.component` (front) | **Étendre** : chips multi-sources (course/force/test/objectif/note/indispo). |
| Pièces jointes / export PDF | — | **Nouveau** : `attachment` + `StorageService` + export PDF programme. |

---

## 2. Architecture technique cible (équivalences au doc)

| Couche doc (Supabase/Next) | Implémentation retenue |
|----------------------------|------------------------|
| Next.js Route Handlers | Contrôleurs Spring `@RestController` REST (convention `/api/...` déjà en place) |
| Supabase PostgreSQL + RLS | PostgreSQL + **Liquibase** (migrations `db/changelog/changes/0XX-*.yaml`) + autorisation service-layer |
| Supabase Auth + OAuth | JWT existant (`JwtService`) ; OAuth Strava/Garmin via `spring-security-oauth2-client` |
| Supabase Storage | `StorageService` (S3 prod / disque dev), URLs signées |
| Supabase Realtime | **SSE** (`SseEmitter`) puis WebSocket/STOMP si besoin |
| Supabase Edge Functions (webhooks/recalc) | Endpoints webhook Spring + `@Scheduler` (déjà un `ReminderScheduler`) + triggers applicatifs dans les services |
| shadcn/Tailwind thème sombre | SCSS Angular existant + **tokens charte §3 du CDC** dans `styles.scss` (variables CSS) |
| Recharts | Charts Angular (lib existante front, sinon ngx-charts / canvas natif) |
| Zustand/React Query | Services Angular + signals/RxJS existants |

**Découpage packages back** (sous `com.coachrun`) :
```
entity/        + physio/  strength/  club/  sync/   (sous-packages par domaine)
engine/        vdot, lactate (LT detection), load (ACWR/sRPE), oneRm (Nuzzo/Epley/Brzycki/RIR),
               intensityDomain, charge (méca/métab)
service/       par domaine (réutilise l'existant)
controller/    REST par domaine
security/      AthleteAccessValidator (extension de ClubAccessValidator)
```

**Découpage features front** (`front/src/app/features/`) — à compléter :
```
physio/ (tests lactate, profil lactate)   strength/ (5 onglets prépa physique)
calendar/ (multi-types)   club/ (coachs, permissions)   sync/   notifications/
```

---

## 3. Détail fonctionnel par domaine (règles métier non négociables)

### 3.A Multi-coach / Club / Permissions
- `coach_athlete_relation` : `coach_id` (référent), `club_id` NULL = **privé**, renseigné = **club**, `is_referent`.
- `athlete_coach_permission` : niveau `read|comment|write`, `expires_at` (remplacement temporaire).
- `club_member` : rôle `owner|coach_principal|coach_assistant`.
- **Règles d'accès** (portées par `AthleteAccessValidator`) :
  - voit un athlète si référent OU permission non expirée OU (owner/principal ET athlète `club`).
  - athlète **privé** jamais visible d'un autre coach, même owner.
  - toute écriture vérifie le niveau `write`.
  - bascule privé→club libre ; club→privé seulement si référent et aucune permission active accordée.

### 3.B Athlète physio + VDOT
- Profil : `vc_ms`, `lt1_ms`, `lt2_ms`, `fc_max/lt1/lt2`, seuils domaines configurables (`vc_domain1_pct`…).
- `athlete_performance` (distance, temps) → **moteur VDOT (Daniels/Riegel)** → `athlete_vdot_pace`
  (allures sec/km par distance), recalcul automatique à chaque perf.
- Données santé **chiffrées au repos** (réutiliser les converters existants).

### 3.C Course : bibliothèque, éditeur, calendrier
- **Prescription en fourchettes uniquement** (min–max %), jamais de valeur sèche.
- Bloc JSONB : `{ type, reps, distance_m|duration_s, recovery, prescription:{ ref_type, min_pct, max_pct } }`
  où `ref_type ∈ pct_lt1|pct_lt2|pct_vc|pct_pace_5km|…`.
- **Calculateur de séance** (moteur Java) : allure/vitesse/FC/RPE/durée/volume estimés pour l'athlète ciblé.
- Calendrier : `scheduled_session` avec **snapshot figé** + `calculated_paces`. L'athlète **déplace** (maj `scheduled_date`, `moved_by_athlete`), **ne supprime ni ne modifie**.
- Éducatifs course = **gammes techniques uniquement** (pas de renfo/gainage → prépa physique).

### 3.D Module Préparation Physique (le plus gros lot)
- **Bibliothèque exercices** : catégories (force_max, hypertrophie, puissance, pliométrie, isométrie, endurance_musculaire, gainage, mobilité, réathlétisation, prévention), groupes musculaires, matériel, niveau, vidéo démo (lien externe), image, consignes, contre-indications, progression/régression (FK), variantes (M2M), recherche full-text (Postgres `tsvector` + GIN, ou `pg_trgm`).
- **Séances à blocs** : `block_type` (échauffement/activation/principal/accessoire/calme), `format` (`classique|emom|amrap|for_time|circuit|isometrie|pliometrie`).
- **Exercice prescrit** : référentiel charge (`kg_fixe|kg_range|pct_rm|pct_rm_range|rm_cible|rm_estime`) **+** effort (`rpe|rir` ± range) indépendants, volume (séries/reps/durée/contacts), tempo `"3-1-X-1"`, repos (fourchette), `max_pain_allowed`.
- **Types de série** (`set_type` + `set_config` JSONB) : `standard|drop_set|super_set|myo_reps|cluster|iso_overcoming|iso_yielding` (UI dédiée par type ; iso masque la ligne séries×reps).
- **Champs demandés à l'athlète** (`required_fields` JSONB, préréglages Débutant/Avancé/Réathlé).
- **Moteur 1RM** : **Nuzzo (2024) par défaut** (`-0.0002·r³ + 0.0363·r² − 2.7814·r + 104.9`, constante **104.9** — test : 15 kg × 6 reps → 16.764 kg), + Epley, Brzycki, RIR-based (`RIR = 10 − RPE`). Zones Lacourpaille (30-50/50-70/>70/30-80 %).
- **Charge cible** = `round_2.5(1RM × %RM / 100)`. **Mise à jour auto e1RM** ; un test direct (`tested`) prévaut sur estimation.
- **Progression auto** : toutes séries OK + RIR > cible + douleur ≤ 2 → +2,5/+5 kg ou +2,5 %.
- **Alertes coach** : douleur > 3 (réathlé), RPE ≥ 9,5, RIR 0 vs 1-2 prescrit, chute charge > 15 %.
- **Tests 1RM** : 4 protocoles (`true_1rm|rep_test_3_5|amrap_test|iso_mvc`) → `strength_test` → maj `athlete_1rm_profile`.
- **Charge méca vs métab (UA)** : méca = `Σ(charge×reps×séries×%1RM)/100`, métab = `RPE×durée_min` → `strength_load_tracking` (onglet Suivi & Analyse).
- **Cycles** : Force max 4 sem, Hypertrophie 6, Pliométrie 4, LCA 8, Achille 10, Hyrox 8 (structure JSONB hebdo).
- **Vidéo** : hors V1 (envoi WhatsApp externe + lien démo seulement).

### 3.E Tests lactate & profil
- Saisie paliers interactive (`speed_ms`, `hr`, `lactate`, `rpe`), toggle min/km ↔ km/h.
- **LT1** = lactate repos + 0,5 mmol/L ; **LT2** = **Dmax modifié** ; `detectLT(steps)` temps réel.
- Profil lactate : courbe multi-tests (axe X allure inversé : lent à gauche), historique LT2.

### 3.F Charge / Data / État de forme
- **État de forme = fatigue + douleur** (jamais RPE) : 🔴 fatigue ≥ 8 OU douleur ≥ 5 ; 🟡 fatigue ≥ 5 OU douleur ≥ 3 ; 🟢 sinon.
- ACWR : sRPE Foster (`RPE × durée`) **course + force confondues**, ATL 7j / CTL 28j / ratio / monotonie + répartition domaines 7j/28j.

### 3.G Sync, messagerie, notifications, calendrier
- Sync : OAuth Strava/Garmin, import `synced_activity`, mapping activité→séance, push séance vers montre (V2).
- Messagerie : conversations coach↔athlète, temps réel (SSE), pièces jointes, messages épinglés.
- Notifications : `athlete_unavailable`, `session_moved`, `new_comment`, `high_fatigue`, `high_pain`, `activity_synced`.
- Calendrier : drag&drop depuis accordéons sidebar (séances, cycles, éducatifs, prépa physique) → toast charges calculées → chip ; chips multi-types colorés (charte §3).

### 3.H Charte graphique (à appliquer dès le sprint 0)
Tokens CSS (thème sombre) : `--bg #0A0E1A`, `--surface #111726`, `--card #161D2E`, `--border #2A3448`,
`--text #E8EDF6`, `--muted #64748B`, `--teal #2DD4BF` (course), `--orange #F97316` (trail),
`--violet #A78BFA` (force), `--green/yellow/red`. Polices `Inter` (UI) + `DM Mono` (données chiffrées).

---

## 4. Plan de migration base de données (Liquibase)

Numérotation à la suite de l'existant (dernier = `015`). Un changelog par lot, RLS remplacé par contraintes + autorisation service.

| Migration | Contenu |
|-----------|---------|
| `016-club-membership-permissions` | `club_member`, `coach_athlete_relation` (privé/club, référent), `athlete_coach_permission` (niveau, expires_at) |
| `017-athlete-physio` | colonnes/​table physio (LT1/LT2/VC/FC/domaines/VDOT) |
| `018-performances-vdot` | `athlete_performance`, `athlete_vdot_pace` |
| `019-session-categories-library` | `session_category` (hiérarchie), enrichissement bibliothèque course (prescription JSONB, favoris, use_count, search_vector) |
| `020-scheduled-session-extend` | `Workout` : `session_snapshot`, `calculated_paces`, `fatigue`, `pain`, `moved_by_athlete`, `original_date`, `objective_id` |
| `021-lactate-tests` | `lactate_test`, `lactate_test_step` |
| `022-training-load-cache` | `training_load_cache` (ATL/CTL/ratio/monotonie/domaines) |
| `023-athlete-groups` | `athlete_group` (couleur/icône), `athlete_group_member` (M2M) |
| `024-pp-exercises` | enums force, `pp_exercise_category`, `pp_exercise`, `pp_exercise_variant`, index GIN |
| `025-pp-sessions` | `strength_session`, `strength_session_block`, `strength_session_exercise` (charge/effort/set_type/set_config) |
| `026-pp-scheduling-results` | `scheduled_strength_session` (required_fields), `strength_result` |
| `027-pp-1rm` | `estimated_1rm`, `athlete_exercise_history`, `athlete_1rm_profile`, `strength_test` |
| `028-pp-cycles-load` | `strength_cycle`, `strength_load_tracking`, `strength_comment` |
| `029-objectives-unavailabilities` | mapping `RaceObjective`→A/B/C, `athlete_unavailability` |
| `030-sync-oauth` | `sync_token`, extension `synced_activity` |
| `031-messaging-notifications` | `conversation`, `message_attachment`, `notification`, `attachment` |

> Toutes dates en UTC (`TIMESTAMPTZ`/`Instant`). JSONB Postgres via `hibernate-types` ou `@JdbcTypeCode(SqlTypes.JSON)` (Hibernate 6, déjà disponible avec Spring Boot 3.2).

---

## 5. Roadmap d'exécution (adaptée du §14 CDC)

Chaque sprint = migrations + entités/repos + services + moteurs + endpoints + écrans + tests (Testcontainers PG, cf. RAF P0-1).

### Sprint 0 — Fondations & adaptation socle
- Tokens charte graphique (§3.H) dans `styles.scss` + `DM Mono`.
- Migration `016` + `AthleteAccessValidator` (privé/club + permissions) + tests d'accès.
- Mapping rôles club sur `User`/`Club` existants. Réglage `@JdbcTypeCode JSON`.

### Sprint 1 — Cœur coach course (MVP)
- Physio athlète (`017`) + Performances/VDOT (`018`) + **moteur VDOT Java**.
- Bibliothèque séances course + catégories (`019`) + **éditeur prescription en fourchettes** + **calculateur de séance**.
- Calendrier course (`020`) + drag&drop + snapshot.
- Dashboard coach (tableaux Route/Trail + **pastilles de forme** fatigue/douleur) + portée (mes athlètes/privés/club/tout).
- Portail athlète : séance du jour + retour (RPE/fatigue/douleur/commentaire).

### Sprint 2 — Physiologie & données
- Tests lactate (`021`) : saisie paliers, **détection LT1/LT2 (Dmax)**, valeurs repos.
- Profil lactate (courbe comparée, axe inversé).
- Data/ACWR (`022`) : sRPE, ATL/CTL/ratio/monotonie, répartition domaines.
- Groupes d'athlètes (`023`) : CRUD coloré, filtres liste athlètes, assignation.

### Sprint 3 — Préparation physique (MVP force)
- Bibliothèque exercices (`024`) : CRUD, recherche full-text, filtres, modal détail.
- Séances force (`025`) format **Classique** + **moteur 1RM (Nuzzo)** + calculateur de charge.
- Calendrier force (`026`) : chip violet, drag&drop, charges calculées.
- Retour athlète force + e1RM + historique (`027`).

### Sprint 4 — Préparation physique (avancé)
- Formats EMOM/AMRAP/Circuit/Iso/Plyo + **types de série** (`set_type`/`set_config`).
- Tests 1RM (4 protocoles) + maj auto + alertes coach + progression auto.
- Cycles de force (`028`).
- Suivi & Analyse : graphiques e1RM/charge/volume/douleur + **charge méca/métab (UA)**.

### Sprint 5 — Multi-coach & finitions athlète
- Écran Club (`s-club`) : coachs, rôles, permissions, invitations, expiration.
- Bascule privé/club (UI fiche athlète) + garde-fous.
- Enrichissement PWA athlète (calendrier déplaçable, historique, messagerie).

### Sprint 6 — Communication, sync & finitions
- Messagerie temps réel (SSE) + pièces jointes + notifications (`031`).
- Sync Strava OAuth (`030`) + import + mapping séance↔activité.
- Objectifs A/B/C + indisponibilités (`029`), export PDF programme, paramètres, peaufinage UI.

> **Dépendance qualité (RAF)** : brancher **Testcontainers PostgreSQL** + smoke E2E dès le Sprint 0
> pour éviter les pièges H2↔PG (bug `lower(bytea)` historique). Chaque nouveau domaine ajoute son smoke test.

---

## 6. Moteurs de calcul — emplacement & contrats (Java, `com.coachrun.engine`)

| Moteur | Entrées → sorties | Notes |
|--------|-------------------|-------|
| `VdotEngine` | perfs → VDOT + allures sec/km | Daniels/Riegel `T2=T1·(D2/D1)^1.06` |
| `LactateThresholdEngine` | paliers → `{lt1, lt2, baseline}` | LT1 = baseline+0,5 ; LT2 = Dmax modifié |
| `IntensityDomainEngine` | vitesse/FC + profil → domaine 1/2/3 | priorité physio (LT1/LT2), fallback FC |
| `SessionCalculatorEngine` | bloc + profil + VDOT → allure/FC/RPE/durée/volume | fourchettes min–max |
| `LoadEngine` | séances (RPE×durée) → ATL/CTL/ratio/monotonie | course + force unifiées |
| `OneRmEngine` | charge/reps/(rir|rpe)/formule → e1RM + zones | Nuzzo par défaut, constante 104.9 |
| `StrengthLoadEngine` | série réalisée → charge méca/métab (UA) + ratio | onglet Suivi |
| `ProgressionEngine` | historique → suggestion (+kg/+%/+volume) | règles §3.D |

Miroir TS minimal côté front (`core/engines/`) pour l'aperçu live des éditeurs ; **le back reste la source de vérité** (recalcul à la sauvegarde).

---

## 7. Risques & points d'attention
1. **Volumétrie du module force** : ~16 tables + UI riche → c'est le plus gros poste (Sprints 3-4). Livrer le format Classique d'abord, le reste en incrémental.
2. **JSONB + Hibernate** : valider `@JdbcTypeCode(SqlTypes.JSON)` sur PG dès le Sprint 0 (snapshots, set_config, structure cycles).
3. **Autorisation vs RLS** : tout passe par `AthleteAccessValidator` — couvrir privé/club + permissions par des tests dédiés (anti-IDOR).
4. **Chiffrement RGPD** : les nouvelles données santé (lactate, douleur) doivent réutiliser les converters chiffrés.
5. **Recalculs automatiques** (VDOT, e1RM, charge) : déclenchés dans les services (équivalent triggers Supabase), idempotents, testés.
6. **Cohérence avec l'existant** : `Workout`/`WorkoutTemplate` portent la course ; ne pas dupliquer un modèle « session » concurrent (cf. décision D1).

---

## 8. Définition de « terminé » par lot
- Migration Liquibase appliquée sur PG réel (Testcontainers) ✅
- Entités + repos + service + moteur + endpoints REST + autorisation testés ✅
- Écran(s) front câblé(s) sur l'API, états vides/erreur gérés (RAF P0-5) ✅
- Smoke test (seed → login → écran) vert sur PG ✅
- Charte graphique respectée (tokens, polices, codes couleur métier) ✅

---

*Plan v1 — base : CoachRun (Spring Boot 3.2.5 / Java 21 / Angular 17). Cible fonctionnelle : DARI Lab Training v1.0.*
*La stack Next.js/Supabase du cahier des charges est volontairement remplacée par les équivalents Spring/Angular ci-dessus.*
</content>
</invoke>
