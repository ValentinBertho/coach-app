# DARI Lab Training — Cahier des charges technique

> **Document destiné à un agent de développement (Codex / Claude Code).**
> Plateforme SaaS de coaching course à pied / trail orientée physiologie, avec module de préparation physique complet et gestion multi-coach.
> Ce document est la source de vérité fonctionnelle et technique. Construire l'application section par section dans l'ordre de la roadmap (§14).

---

## 1. Vision produit

DARI Lab Training est une application de coaching à distance pour entraîneurs de course à pied et de trail. Elle combine :

- **Un moteur course** basé sur la physiologie : seuils lactiques (LT1/LT2), vitesse critique (VC), VDOT, domaines d'intensité, profil lactate.
- **Un moteur force / préparation physique** : prescription multi-référentiel (%RM, RIR, RPE, kg), calcul du 1RM, cycles, suivi de charge mécanique/métabolique.
- **Une couche multi-coach / club** : plusieurs entraîneurs partagent des athlètes avec des permissions graduées.

Principe directeur : **un seul athlète, un seul calendrier, deux moteurs de prescription** (course + force) qui partagent le même historique et le même suivi de charge.

### Principes de conception non négociables

1. **Prescription en fourchettes** : toute prescription (allure, charge, effort) se fait en fourchettes (min–max), jamais en valeur unique sèche.
2. **État de forme ≠ RPE** : l'indicateur de forme d'un athlète (pastille verte/orange/rouge) se calcule à partir de la **fatigue + douleur**, jamais du RPE de séance.
3. **L'athlète déplace, ne modifie pas** : un athlète peut déplacer une séance dans son calendrier mais ne peut ni la supprimer ni en modifier le contenu.
4. **Récupération = course uniquement** : les séances de récupération active sont des séances de course, pas de force.
5. **Recalcul automatique** : VDOT, e1RM et zones de travail se recalculent automatiquement à partir des performances et retours saisis.
6. **Le coach choisit la complexité** : pour chaque séance de force, le coach sélectionne les champs que l'athlète devra renseigner, selon son niveau.

---

## 2. Stack technique

| Couche | Technologie |
|--------|-------------|
| Frontend web | Next.js 14 (App Router), React 18, TypeScript |
| Styling | Tailwind CSS, thème sombre (voir §3) |
| Backend / DB | Supabase (PostgreSQL 15 + Row Level Security) |
| Auth | Supabase Auth (email + OAuth Strava / Garmin) |
| Stockage fichiers | Supabase Storage (images, PDF) |
| Mobile athlète | React Native (Expo) — partage la logique métier via package partagé |
| Graphiques | Canvas natif (avec `devicePixelRatio`) ou Recharts |
| Temps réel | Supabase Realtime (messagerie, notifications) |
| Intégrations | Strava API, Garmin Connect, (extensible : Coros, Polar, Suunto) |

**Conventions de code** : TypeScript strict, composants fonctionnels + hooks, server components par défaut, API REST via Route Handlers Next.js. Toutes les dates en UTC en base, converties côté client.

---

## 3. Charte graphique (thème sombre)

| Token | Valeur | Usage |
|-------|--------|-------|
| `--bg` | `#0A0E1A` | Fond principal (nuit marine) |
| `--surface` | `#111726` | Surfaces secondaires |
| `--card` | `#161D2E` | Cartes |
| `--border` | `#2A3448` | Bordures |
| `--text` | `#E8EDF6` | Texte principal |
| `--muted` | `#64748B` | Texte secondaire |
| `--teal` | `#2DD4BF` | Accent principal / course / e1RM |
| `--orange` | `#F97316` | Trail / charge métabolique |
| `--violet` | `#A78BFA` | Force / prépa physique |
| `--green` | `#10B981` | État OK |
| `--yellow` | `#FBBF24` | Attention / volume |
| `--red` | `#EF4444` | Alerte / douleur |

**Typographie** : `Inter` pour l'UI, `DM Mono` pour les données physiologiques et chiffrées.

**Codes couleur métier** :
- Discipline : Route = teal, Trail = orange.
- Domaines d'intensité : Domaine 1 (vert), Domaine 2 (jaune), Domaine 3 (rouge).
- État de forme : vert (frais), orange (fatigue/douleur modérée), rouge (fatigue/douleur élevée).
- Statut athlète : Privé (violet 🔒), Club (teal 🏛️).
- Formats de bloc force : Classique (violet), EMOM (jaune), AMRAP (orange), For Time (rouge), Circuit (teal).

---

## 4. Rôles & modèle d'accès

### Rôles utilisateurs
| Rôle | Portée |
|------|--------|
| `coach` | Gère ses athlètes (référent) + ceux pour lesquels il a une permission |
| `athlete` | Consulte ses données, déplace des séances, renseigne ses retours |
| `admin` | Gestion globale des comptes et facturation |

### Rôles club
| Rôle club | Capacités |
|-----------|-----------|
| `owner` | Tout : inviter/retirer des coachs, voir tous les athlètes du club, facturation, dissolution |
| `coach_principal` | Voit tous les athlètes du club (lecture par défaut), gère ses propres athlètes en écriture, peut avoir des athlètes privés |
| `coach_assistant` | Accès limité aux athlètes assignés ou permissions accordées |

### Niveaux de permission (par athlète)
| Niveau | Capacités |
|--------|-----------|
| `read` | Dashboard, calendrier, profil physio, analyses — aucune modification |
| `comment` | Lecture + messagerie + retours de séance |
| `write` | Accès complet — prescrire, modifier, remplacer le coach référent |

### Distinction privé / club
Chaque relation coach-athlète a un `ownership_type` :
- **`private`** : athlète visible uniquement par le coach référent (clientèle personnelle hors club). Invisible même pour l'Owner.
- **`club`** : athlète visible par les coachs du club selon les permissions.

Bascule privé → club : libre. Bascule club → privé : autorisée seulement si le coach est référent ET qu'aucune permission active n'a été accordée à un autre coach.

### Règles RLS (Row Level Security)
- Un coach voit un athlète s'il en est le référent (`coach_athlete_relations`) OU s'il a une permission explicite non expirée (`athlete_coach_permissions`).
- Un Owner/coach_principal voit les athlètes `club` du club, jamais les athlètes `private` d'un autre coach.
- Un athlète ne voit que ses propres données.
- Toute écriture vérifie le niveau de permission du coach sur l'athlète ciblé.

---

## 5. Modèle de données

Le schéma PostgreSQL complet (43 tables, ~20 ENUMs) est fourni dans le fichier `DARI_Lab_Training_Architecture.md` (section 3). Il doit être repris **tel quel** comme migrations Supabase. Vue d'ensemble par domaine :

### Domaine Identité & Club
`profiles`, `clubs`, `club_members`, `athlete_club_membership`, `coach_athlete_relations`, `athlete_coach_permissions`

### Domaine Athlète
`athlete_profiles` (seuils physio, VDOT, domaines), `athlete_groups`, `athlete_group_members`, `athlete_performances`, `athlete_vdot_paces`, `objectives`, `athlete_unavailabilities`

### Domaine Course / Séances
`session_categories`, `sessions_library`, `cycles_library`, `scheduled_sessions`

### Domaine Tests physiologiques
`lactate_tests`, `lactate_test_steps`

### Domaine Synchronisation
`synced_activities`, `sync_tokens`

### Domaine Communication
`conversations`, `messages`, `message_attachments`, `notifications`, `attachments`

### Domaine Charge
`training_load_cache` (ACWR global course+force unifié)

### Domaine Préparation Physique
`pp_exercise_categories`, `pp_exercises`, `pp_exercise_variants`, `strength_sessions`, `strength_session_blocks`, `strength_session_exercises`, `scheduled_strength_sessions`, `strength_results`, `estimated_1rm`, `athlete_exercise_history`, `athlete_1rm_profile`, `strength_tests`, `strength_cycles`, `strength_comments`, `strength_load_tracking`

**Règles transverses** :
- Toutes les séances planifiées stockent un `session_snapshot` JSONB (copie figée de la prescription au moment de l'assignation) pour que les modifications ultérieures de la bibliothèque n'altèrent pas l'historique.
- Les compteurs `use_count` / `last_used_at` sont mis à jour par trigger.
- `search_vector` (tsvector) sur les exercices pour la recherche full-text (index GIN).

---

## 6. Moteurs de calcul

### 6.1 VDOT (course)
Calcul du VDOT (Daniels) à partir des performances de `athlete_performances`, puis dérivation automatique des allures d'entraînement dans `athlete_vdot_paces`. Recalcul par trigger à chaque nouvelle performance.

### 6.2 Seuils lactiques (test lactate)
À partir des paliers saisis (`lactate_test_steps`) :
- **LT1** = lactate de repos (baseline) + 0,5 mmol/L.
- **LT2** = méthode **Dmax modifié** : tracer la droite reliant le point LT1 au dernier point, LT2 = point de la courbe à distance perpendiculaire maximale de cette droite.
- Fonction `detectLT(steps)` retourne `{ lt1, lt2, baseline, lt1Threshold }`.
- Carte « Valeurs au repos » : lactate de repos + FC de repos + FC max saisissables.

### 6.3 Domaines d'intensité
Trois domaines bornés par % de VC et % de FC (configurables par athlète via `vc_domain1_pct`, etc.). Domaine 1 (sous LT1), Domaine 2 (entre LT1 et LT2), Domaine 3 (au-dessus de LT2).

### 6.4 Charge d'entraînement (ACWR global)
- Charge de séance = `RPE × durée_min` (sRPE Foster), course **et** force confondues.
- `acute_load_7d` (ATL), `chronic_load_28d` (CTL), ratio ACWR, monotonie. Stocké dans `training_load_cache`.

### 6.5 Estimation du 1RM — **méthode Nuzzo (2024) / Lacourpaille par défaut**
Quatre formules, sélectionnables par le coach. La formule **Nuzzo** est le défaut recommandé (polynôme degré 3, plus précis sur hautes répétitions que les formules linéaires).

```javascript
// NUZZO et al. (2024) — RECOMMANDÉE (méthode Pr. Lacourpaille, Univ. Nantes)
function nuzzoPct1RM(reps) {
  return -0.0002 * reps**3 + 0.0363 * reps**2 - 2.7814 * reps + 104.9;
}
function nuzzo1RM(weight, reps) {
  return weight * 100 / nuzzoPct1RM(reps);
}
// VALIDÉ : 15 kg × 6 reps → 16.764 kg (constante = 104.9, pas 104.09)

// EPLEY
function epley1RM(weight, reps) {
  return reps === 1 ? weight : weight * (1 + reps / 30);
}
// BRZYCKI
function brzycki1RM(weight, reps) {
  return reps === 1 ? weight : weight * (36 / (37 - reps));
}
// RIR-BASED (table % du 1RM selon reps réelles = reps + RIR)
const RIR_PCT_TABLE = {1:100,2:95.5,3:92.2,4:89.2,5:86.3,6:83.7,7:81.1,8:78.6,9:76.2,10:73.9,11:71.6,12:69.4};
function rirBased1RM(weight, repsDone, rir) {
  const eff = repsDone + rir;
  return weight / ((RIR_PCT_TABLE[Math.min(eff,12)] || 65) / 100);
}
```

**Conversion** : `RIR = 10 − RPE`.

**Zones de travail dérivées (méthode Lacourpaille)** :
| Zone | % 1RM |
|------|-------|
| Puissance-vitesse | 30–50 % |
| Puissance-force | 50–70 % |
| Force maximale | > 70 % |
| Hypertrophie | 30–80 % |

**Mise à jour automatique du 1RM** : à chaque série performante, recalculer le e1RM. Si la nouvelle estimation dépasse le `rm_kg` courant de `athlete_1rm_profile`, le mettre à jour (`source = 'estimated'`). Un test direct (`strength_tests`, source `tested`) prévaut toujours sur une estimation.

### 6.6 Charge cible depuis %RM
`charge_cible = round_2.5( 1RM × %RM / 100 )` — arrondi au palier de 2,5 kg.

### 6.7 Progression automatique (force)
Si toutes les séries réalisées **et** RIR > cible **et** douleur ≤ 2 : proposer +2,5 kg (si charge < 40 kg) ou +5 kg, ou +2,5 % en %RM, ou augmentation du volume.

### 6.8 Alertes coach (force)
- Douleur > 3/10 en réathlétisation → alerte haute.
- RPE ≥ 9,5 → alerte moyenne.
- RIR 0 alors que 1–2 prescrit → alerte moyenne.
- Chute de charge > 15 % vs séance précédente → alerte haute.

### 6.9 Charge mécanique vs métabolique (UA) — spécifique prépa physique
- **Charge mécanique (UA)** = `Σ (charge_kg × reps × séries × %1RM) / 100` → stress structurel (tendons, articulations).
- **Charge métabolique (UA)** = `RPE_séance × durée_min` → fatigue centrale / coût énergétique.
- **Ratio Méca/Métab** : élevé = lourd peu fatigant (surveiller tendons) ; bas = métabolique (surveiller récupération).
- Stocké dans `strength_load_tracking`. Affiché dans l'onglet Suivi & Analyse du module force (graphique double-barres + tableau hebdo avec marquage deload si ≤ 2 séances/semaine).

---

## 7. Module Préparation Physique — spécification détaillée

### 7.1 Types de séances / formats de bloc
Chaque bloc d'une séance a un `format` (`block_format`) : `classique`, `emom`, `amrap`, `for_time`, `circuit`, `isometrie`, `pliometrie`. Les blocs EMOM/AMRAP/Circuit ont des paramètres dédiés (durée totale, tours, temps travail/repos).

### 7.2 Types de série avancés (`set_type` + `set_config` JSONB)
Au sein d'un exercice, le coach choisit un **type de série** qui change la structure de l'effort :

| `set_type` | Description | `set_config` (JSONB) |
|------------|-------------|----------------------|
| `standard` | Séries droites classiques | — |
| `drop_set` | Charge décroissante sans repos jusqu'à l'échec | `{ drops: [{pct_drop, reps}] }` |
| `super_set` | Exercices appariés alternés (A1/A2) | `{ paired_exercise_ids, rest_between_pairs_sec, label }` |
| `myo_reps` | Activation + mini-séries à micro-repos | `{ activation_reps, mini_sets, mini_reps, micro_rest_sec }` |
| `cluster` | Reps fractionnées avec repos intra-série | `{ cluster_reps:[2,2,2], intra_rest_sec }` |
| `iso_overcoming` | Pousser/tirer max contre résistance fixe | `{ push_sec, intent, reps, rest_sec, joint_angle_deg }` |
| `iso_yielding` | Retenir une charge le plus longtemps | `{ hold_sec, load_pct_rm, joint_angle_deg }` |

Pour `iso_overcoming` et `iso_yielding`, l'UI masque la ligne « Séries × Reps » standard au profit de la config spécifique.

### 7.3 Prescription d'un exercice
Chaque exercice prescrit possède **un référentiel de charge** ET **un référentiel d'effort**, indépendants :
- Charge (`charge_ref_type`) : `kg_fixe`, `kg_range`, `pct_rm`, `pct_rm_range`, `rm_cible`, `rm_estime`.
- Effort (`effort_ref_type`) : `rpe`, `rpe_range`, `rir`, `rir_range`.
- Volume : séries, reps (fixe ou fourchette), durée (iso), contacts (plyo).
- Tempo : format `"3-1-X-1"` (excentrique-pause bas-concentrique-pause haut).
- Repos : fourchette en secondes.
- Réathlétisation : `max_pain_allowed` (0–10).

### 7.4 Champs demandés à l'athlète (adaptatif)
Pour chaque séance planifiée, `required_fields` (JSONB) définit ce que l'athlète doit renseigner. Préréglages :
| Niveau | charge | reps | RPE | RIR | douleur | commentaire |
|--------|:------:|:----:|:---:|:---:|:-------:|:-----------:|
| Débutant | ✓ | ✓ | — | — | — | ✓ |
| Avancé | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Réathlétisation | ✓ | ✓ | ✓ | — | ✓ | ✓ |

### 7.5 Bibliothèque d'exercices
Catégories : Force max, Hypertrophie, Puissance, Pliométrie, Isométrie, Endurance musculaire, Gainage, Mobilité, Réathlétisation, Prévention. Chaque exercice porte : groupes musculaires (array), matériel (array), niveau, objectif, lien vidéo démo (externe), image, consignes, notes techniques, contre-indications, progression/régression (FK vers un autre exercice), variantes (M2M).

### 7.6 Cycles de force
Cycles types : Force max 4 sem, Hypertrophie 6 sem, Pliométrie 4 sem, LCA phase force 8 sem, Tendon d'Achille 10 sem, Prépa Hyrox 8 sem. Structure JSONB par semaine avec progression de charge (% ajusté).

### 7.7 Test 1RM
Modal avec 4 protocoles (`rm_test_protocol`) : `true_1rm`, `rep_test_3_5`, `amrap_test`, `iso_mvc`. Sélecteur de formule (Nuzzo par défaut). Calcul temps réel du 1RM + affichage des 4 zones de travail Lacourpaille. Enregistrement dans `strength_tests` → met à jour `athlete_1rm_profile`.

### 7.8 Vidéo (hors-périmètre V1)
L'analyse vidéo native n'est **pas** implémentée en V1. Les athlètes envoient leurs vidéos d'exécution au coach **par WhatsApp** (canal externe déjà adopté). Seul un **lien externe** de vidéo de démonstration d'exercice (YouTube/Vimeo) est stocké dans `pp_exercises.video_url` et affiché en lecture. La fonctionnalité d'upload + annotations coach pourra être réintégrée en V2 si le besoin se confirme.

---

## 8. Calendrier

Un calendrier unique par athlète accueille tous les types d'événements :
| Type | Source | Affichage |
|------|--------|-----------|
| Séance course | `scheduled_sessions` | chip teal, barres de domaine 1/2/3 |
| Séance prépa physique | `scheduled_strength_sessions` | chip violet, icône 💪, badge de format |
| Test | `lactate_tests` | chip dédié |
| Objectif | `objectives` | chip 🎯 A/B/C |
| Note | — | chip note |
| Indisponibilité | `athlete_unavailabilities` | chip grisé |

**Drag & drop** : depuis les accordéons de la sidebar (bibliothèque séances, cycles, éducatifs course, prépa physique) vers une case jour. Au drop : toast de confirmation affichant les charges calculées pour cet athlète, puis création d'un chip. Les séances de force affichent leur format coloré et un badge 🩹 si suivi douleur actif.

**Règle** : l'athlète peut déplacer un chip (met à jour `scheduled_date`, marque `moved_by_athlete`), jamais le supprimer ni le modifier.

---

## 9. Écrans (côté coach)

| Écran | ID | Contenu clé |
|-------|----|-----|
| Dashboard | `s-dashboard` | Tableaux Route/Trail, pastilles de forme (clic → popover fatigue/douleur), sélecteur de portée (Mes athlètes / Mes privés / Mes athlètes club / Tout le club), badge de rôle |
| Liste athlètes | `s-athletes` | Tableau avec statut privé/club, **filtres par groupe personnalisé**, colonne groupes, gestion des groupes |
| Fiche athlète | `s-athlete-detail` | En-tête (discipline, forme, statut, badges de groupes + menu d'assignation), bascule privé/club, profil physio, historique |
| Calendrier | `s-calendar` | Vue semaine, drag & drop, chips multi-types |
| Éditeur de séance course | `s-session-editor` | Blocs échauffement/corps/retour calme, prescription en fourchettes, calculateur d'allures |
| Bibliothèque séances | `s-library` | Arbre 3 niveaux éditable (renommer/ajouter/supprimer) |
| Éducatifs course | `s-exercises` | **Gammes techniques de course uniquement** (Technique, Amplitude). PAS de renforcement/gainage (→ prépa physique) |
| Préparation Physique | `s-strength` | 5 onglets : Bibliothèque Exercices, Bibliothèque Séances, Cycles, Tests 1RM, Suivi & Analyse |
| Tests | `s-tests` | Saisie de paliers lactate interactive, toggle min/km ↔ km/h, détection LT1/LT2 temps réel, valeurs au repos |
| Profil Lactate | `s-lactate` | Courbe de comparaison multi-tests (axe X allure inversé : lent à gauche) |
| Data | `s-data` | ACWR, monotonie, RPE×durée, sélecteur athlète, courbes |
| Messages | `s-messages-coach` | Messagerie temps réel |
| Synchronisations | `s-sync` | Connexions Strava/Garmin |
| Club | `s-club` | Tableau des coachs, rôles, permissions, invitations |
| Paramètres | `s-settings` | Préférences |

### Module Préparation Physique — détail des 5 onglets
1. **Bibliothèque Exercices** : filtres (catégorie, groupe musculaire, matériel, niveau), recherche, cartes exercice, modal détail.
2. **Bibliothèque Séances** : par format, favoris, badge réathlé, éditeur de séance par blocs.
3. **Cycles** : cartes cycles avec progression hebdo de charge.
4. **Tests 1RM** : tableau profil de force (1RM, source auto/testé, dernier test, évolution), modal de test.
5. **Suivi & Analyse** : sélecteur athlète, tableau de suivi (e1RM, charge, tendance, douleur), graphique multi-courbes (e1RM/charge/volume/douleur), **tableau de charge mécanique/métabolique en UA**.

### Éditeur de séance force (modal)
Blocs échauffement / activation / principal / accessoires / retour au calme. Sélecteur de format par bloc. Sélecteur de type de série avec panneau de config dynamique. Calculateur de charge en temps réel basé sur le e1RM de l'athlète. Bloc EMOM minute par minute. Config des champs demandés à l'athlète avec préréglages.

---

## 10. Écrans (côté athlète, mobile)

- **Séance du jour** : exercice par exercice (lien démo, consigne, séries×reps, charge cible, RIR/RPE cible, tempo, repos). Saisie après chaque exercice des champs demandés (`required_fields`). Saisie de fin de séance (RPE séance, fatigue, douleur, commentaire).
- **Mon calendrier** : peut déplacer une séance, pas la modifier.
- **Mon historique** : progression e1RM par exercice (lecture seule).
- **Messagerie** avec le coach.

---

## 11. Groupes d'athlètes (catégories personnalisées)

Le coach crée des groupes nommés et colorés (`athlete_groups`) : ex « Élite trail », « Prépa marathon automne », « Réathlé », « Débutants ». Un athlète appartient à 0..n groupes (`athlete_group_members`, M2M). Les groupes sont rattachables au coach (`coach_id`) ou partagés au club (`club_id`).

**UI** : barre de filtres par groupe sur la liste athlètes, colonne groupes, modal de gestion (CRUD groupe avec nom/icône/couleur), assignation via menu déroulant sur la fiche athlète.

---

## 12. Intégrations externes

- **Strava / Garmin** : OAuth, import automatique des activités dans `synced_activities`, tokens dans `sync_tokens`. Mapping activité → séance planifiée. Possibilité de pousser une séance vers la montre.
- Extensible à Coros, Polar, Suunto (enum `sync_provider` déjà prévu).

---

## 13. API (Route Handlers Next.js)

Routes REST principales (le détail figure dans `DARI_Lab_Training_Architecture.md` §API). Familles :
- `AUTH` (inscription club/coach/athlète, OAuth)
- `CLUB` (coachs, rôles, permissions, invitations)
- `ATHLETES` (CRUD, profil physio, groupes, performances)
- `SESSIONS` (bibliothèque, assignation, calendrier)
- `TESTS` (lactate, détection seuils)
- `SYNC` (Strava/Garmin)
- `PP` (exercices, séances force, calcul e1RM, résultats, progression, cycles, tests 1RM, charge méca/métab)

Toutes les routes appliquent les règles RLS via Supabase (le client serveur agit avec le JWT de l'utilisateur).

---

## 14. Roadmap de développement (ordre de construction)

### Sprint 0 — Fondations
1. Setup Next.js 14 + Supabase + Tailwind (thème §3).
2. Migrations : domaine Identité & Club + RLS.
3. Auth (email + invitation coach/athlète).

### Sprint 1 — Cœur coach course (MVP)
4. Domaine Athlète (profils, groupes, performances, VDOT).
5. Bibliothèque séances course + éditeur (prescription en fourchettes).
6. Calendrier + drag & drop séances course.
7. Dashboard coach (pastilles de forme).

### Sprint 2 — Physiologie & données
8. Tests lactate (saisie paliers, LT1/LT2, valeurs repos).
9. Profil lactate (courbe comparée).
10. Data / ACWR (charge unifiée).
11. Synchronisation Strava/Garmin.

### Sprint 3 — Préparation physique (MVP force)
12. Bibliothèque exercices (CRUD, recherche, catégories).
13. Éditeur de séance force, format Classique, calculateur de charge (Nuzzo).
14. Intégration calendrier (drag & drop force, chip violet).
15. Retour athlète simple + e1RM + historique.

### Sprint 4 — Préparation physique (avancé)
16. Formats EMOM/AMRAP/Circuit/Iso/Plyo + types de série avancés (`set_type`).
17. Tests 1RM (4 protocoles) + mise à jour auto.
18. Cycles de force.
19. Suivi & Analyse : graphiques + charge mécanique/métabolique (UA).
20. (Vidéo native reportée en V2 — remplacée par envoi WhatsApp + lien démo externe)

### Sprint 5 — Multi-coach & app athlète
21. Écran Club (coachs, rôles, permissions, invitations).
22. Distinction privé/club + bascule.
23. App mobile athlète (séance du jour, retours, calendrier, messagerie).

### Sprint 6 — Communication & finitions
24. Messagerie temps réel + notifications.
25. Pièces jointes, export PDF de programme.
26. Paramètres, préférences, peaufinage UI.

---

## 15. Annexes

- **Wireframes interactifs** : `dari-lab-wireframes.html` (référence visuelle exhaustive de tous les écrans, à ouvrir dans un navigateur).
- **Architecture détaillée + schéma SQL complet** : `DARI_Lab_Training_Architecture.md`.
- **Source de la méthode 1RM** : Nuzzo et al. (2024), outil du Pr. Lilian Lacourpaille (Université de Nantes). Constante du polynôme = **104.9**.

---

*Fin du cahier des charges — DARI Lab Training v1.0*
