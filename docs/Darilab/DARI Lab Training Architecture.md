# DARI Lab Training — Architecture Complète
**Version 1.0 — Document CTO/Product**

---

## SOMMAIRE

1. Architecture Produit
2. Architecture Technique
3. Architecture Base de Données (schéma SQL)
4. Parcours Utilisateurs Coach & Athlète
5. Wireframes (tous les écrans)
6. APIs
7. Composants React
8. Priorités MVP
9. Roadmap V1 / V2 / V3

---

## 1. ARCHITECTURE PRODUIT

### Vision Produit
DARI Lab Training est une plateforme SaaS de coaching course à pied orientée physiologie. Elle positionne LT1, LT2, VC et lactate comme données centrales de toute prescription, là où les outils concurrents (Nolio, TrainingPeaks) restent sur des métriques de performance génériques.

### Utilisateurs & Rôles

L'application fonctionne en mode **Club/Équipe** : plusieurs coachs peuvent appartenir à une même structure (club) et partager l'accès aux mêmes athlètes, avec des niveaux de droits différenciés.

| Rôle | Portée | Droits principaux |
|------|--------|-------------------|
| Owner (Propriétaire du club) | Club | Tout faire + gérer les coachs, facturation, suppression du club |
| Coach principal | Athlètes qui lui sont assignés | Tout créer/modifier/supprimer sur ses athlètes |
| Coach assistant | Athlètes du club (lecture) ou assignés (écriture) | Configurable : lecture seule ou écriture selon assignation |
| Athlète | Son propre profil | Consulter, déplacer des séances, renseigner retours |
| Admin DARI | Toutes structures | Gestion des comptes, billing global |

**Principe d'assignation** : chaque athlète a un **coach référent** (responsable principal de sa programmation) mais peut être **visible par tous les coachs du club** selon les permissions définies par l'Owner. Un coach assistant peut être autorisé à :
- Voir uniquement (dashboard, calendrier, données)
- Voir + commenter (messagerie, retours)
- Voir + modifier (remplacement complet, ex: pendant les vacances du coach référent)

### Modules Fonctionnels

```
DARI Lab Training
├── AUTH
│   ├── Inscription Club / Owner
│   ├── Invitation Coach (au sein du club)
│   ├── Invitation Athlète
│   └── OAuth (Strava, Garmin)
│
├── CLUB (nouveau)
│   ├── Gestion des coachs (inviter, rôles, retirer)
│   ├── Vue d'ensemble club (tous athlètes, tous coachs)
│   ├── Attribution coach référent par athlète
│   └── Permissions par coach
│
├── COACH
│   ├── Dashboard (mes athlètes + vue club si autorisé)
│   ├── Gestion Athlètes
│   ├── Calendrier (semaine/mois)
│   ├── Bibliothèque Séances (partagée club ou personnelle)
│   ├── Bibliothèque Cycles
│   ├── Tests Physiologiques
│   ├── Profil Lactate
│   ├── Messagerie

│   ├── Synchronisations
│   └── Paramètres
│
├── ATHLETE
│   ├── Calendrier
│   ├── Séance du Jour
│   ├── Retour de Séance
│   ├── Messages
│   └── Profil
│
└── MOTEURS CALCUL
    ├── VDOT Engine
    ├── Session Calculator
    ├── Load Calculator (ATL/CTL/Ratio)
    └── Intensity Domain Classifier
```

### Règles Métier Critiques

- **Prescription = fourchettes uniquement** (ex: 95-102% LT2). Jamais de valeur fixe.
- **État de forme = Fatigue + Douleur uniquement** (pas RPE).
  - 🔴 Rouge : fatigue ≥ 8 OU douleur ≥ 5
  - 🟡 Orange : fatigue ≥ 5 OU douleur ≥ 3
  - 🟢 Vert : sinon
- **Athlète interdit de supprimer/modifier le contenu** d'une séance. Il peut seulement déplacer et renseigner des retours.
- **Récupération = course uniquement** (pas mobilité, pas gainage).
- **Recalcul VDOT = automatique** à chaque mise à jour de performance.

---

## 2. ARCHITECTURE TECHNIQUE

### Stack Technologique

```
Frontend Web        Next.js 14 (App Router) + TypeScript
Frontend Mobile     React Native (Expo) — partage de logique avec Next.js
UI Components       shadcn/ui + Tailwind CSS
State Management    Zustand + React Query (TanStack Query v5)
Backend / BaaS      Supabase (PostgreSQL + Auth + Storage + Realtime)
API                 Next.js Route Handlers (REST) + Supabase Edge Functions
Calculs moteurs     TypeScript pur (VDOT, Load, Domain Classifier)
File Storage        Supabase Storage (PDF, images)
Realtime            Supabase Realtime (messagerie, notifications)
Sync Wearables      Webhooks Strava / Garmin Connect IQ / COROS API
Email               Resend
Payments            Stripe
Hosting             Vercel (web) + EAS (mobile)
Monitoring          Sentry + Vercel Analytics
```

### Architecture Applicative

```
┌─────────────────────────────────────────┐
│           CLIENT LAYER                   │
│  Next.js Web App    React Native App     │
│  (coach + athlete)  (athlete mobile)     │
└────────────┬────────────────────────────┘
             │ HTTPS / WebSocket
┌────────────▼────────────────────────────┐
│           API LAYER                      │
│  Next.js Route Handlers                  │
│  /api/athletes  /api/sessions            │
│  /api/vdot      /api/sync                │
│  Supabase Edge Functions (webhooks)      │
└────────────┬────────────────────────────┘
             │
┌────────────▼────────────────────────────┐
│           DATA LAYER                     │
│  Supabase PostgreSQL (RLS activé)        │
│  Supabase Storage                        │
│  Supabase Realtime                       │
└────────────┬────────────────────────────┘
             │
┌────────────▼────────────────────────────┐
│           EXTERNAL SERVICES              │
│  Strava API  │  Garmin API              │
│  COROS API   │  Polar API  │  Suunto    │
└─────────────────────────────────────────┘
```

### Row Level Security (RLS) — Principe
- Un coach voit les athlètes dont il est **référent** (`coach_athlete_relations`), qu'ils soient privés ou club.
- Un athlète **privé** (`club_id IS NULL`) n'est jamais visible par un autre coach, même Owner du club — aucune permission ne peut s'y appliquer.
- Un athlète **club** (`club_id` renseigné) peut être vu par d'autres coachs du club via une permission explicite (`athlete_coach_permissions`) ou via le rôle Owner/coach principal en lecture par défaut.
- Un athlète voit uniquement ses propres données, quel que soit le nombre de coachs y ayant accès.
- Toute écriture (séance, retour, modification profil) vérifie le niveau de permission du coach sur cet athlète précis avant d'autoriser l'action.

```sql
-- Policy RLS : lecture d'un athlète par un coach
-- Distingue explicitement privé (jamais partagé) et club (partageable)
CREATE POLICY "coach_can_read_athlete" ON athlete_profiles
FOR SELECT USING (
  -- Cas 1 : je suis le coach référent (privé ou club)
  EXISTS (
    SELECT 1 FROM coach_athlete_relations car
    WHERE car.athlete_id = athlete_profiles.athlete_id
      AND car.coach_id = auth.uid() AND car.active = true
  )
  OR
  -- Cas 2 : athlète CLUB + permission explicite accordée
  EXISTS (
    SELECT 1 FROM coach_athlete_relations car
    JOIN athlete_coach_permissions acp ON acp.athlete_id = car.athlete_id
    WHERE car.athlete_id = athlete_profiles.athlete_id
      AND car.is_private = false  -- jamais sur un athlète privé
      AND acp.coach_id = auth.uid()
      AND (acp.expires_at IS NULL OR acp.expires_at > NOW())
  )
  OR
  -- Cas 3 : athlète CLUB + je suis Owner/coach principal du même club (lecture par défaut)
  EXISTS (
    SELECT 1 FROM coach_athlete_relations car
    JOIN club_members cm ON cm.club_id = car.club_id
    WHERE car.athlete_id = athlete_profiles.athlete_id
      AND car.is_private = false
      AND cm.coach_id = auth.uid()
      AND cm.club_role IN ('owner', 'coach_principal')
      AND cm.active = true
  )
);
```

---

## 1.bis ARCHITECTURE MULTI-COACH / CLUB

### Athlètes privés vs athlètes club
Chaque athlète créé par un coach est, par défaut, **privé** : visible uniquement par son coach référent, exactement comme dans le modèle mono-coach initial. Le coach choisit explicitement de **rattacher un athlète au club** s'il souhaite le rendre potentiellement visible par ses collègues.

| | Athlète privé | Athlète club |
|---|---|---|
| `club_id` sur la relation | `NULL` | renseigné |
| Visible par d'autres coachs | Jamais, sous aucune condition | Oui, selon permissions ou rôle club |
| Cas d'usage typique | Clientèle personnelle du coach, en dehors de sa structure | Athlètes suivis dans le cadre du club/équipe |
| Réversible | Le coach peut rattacher un privé au club à tout moment | Le coach peut retirer un athlète du club (redevient privé) |

Cette distinction permet à un coach d'avoir **deux activités simultanées dans la même app** : son activité personnelle (athlètes privés, non facturés au club, invisibles des autres) et son activité au sein du club (athlètes partagés, visibles selon permissions).

### Principe général
Un **club** regroupe plusieurs coachs. Chaque athlète appartient au club et a un **coach référent** unique (responsable de sa programmation). Les autres coachs du club peuvent obtenir un accès ponctuel ou permanent selon 3 niveaux :

| Niveau | Capacités |
|--------|-----------|
| **Lecture** | Dashboard, calendrier, profil physio, analyses — aucune modification |
| **Commentaire** | Lecture + messagerie + retours de séance |
| **Écriture** | Accès complet — peut prescrire, modifier, remplacer le coach référent (ex: vacances) |

### Rôles du club

| Rôle club | Peut faire |
|-----------|-----------|
| **Owner** | Tout : inviter/retirer des coachs, voir tous les athlètes du club, gérer la facturation, dissoudre le club |
| **Coach principal** | Voir tous les athlètes **du club** (lecture par défaut), gérer ses propres athlètes en écriture, inviter des athlètes, conserver des **athlètes privés** non visibles par le club |
| **Coach assistant** | Voir uniquement les athlètes qui lui sont assignés ou pour lesquels une permission a été accordée |

### Athlètes privés vs athlètes du club
Chaque athlète géré par un coach a un statut d'appartenance (`ownership_type`) :

| Statut | Visibilité | Cas d'usage |
|--------|-----------|-------------|
| **Privé** (`private`) | Uniquement le coach référent. Invisible pour les autres coachs du club, même l'Owner. | Clientèle personnelle du coach en dehors de son activité club (coaching freelance, amis, famille) |
| **Club** (`club`) | Visible par tous les coachs du club selon les permissions définies (lecture par défaut pour les coachs principaux). | Athlètes inscrits officiellement au club |

Un coach peut **basculer un athlète de privé à club** (et inversement, sous conditions) depuis sa fiche athlète. Le passage de privé → club est libre ; le passage de club → privé nécessite que le coach soit le référent et qu'aucune permission active n'ait été accordée à un autre coach.

### Cas d'usage couverts
- **Remplacement temporaire** : un coach principal part en vacances → il accorde un accès "écriture" avec `expires_at` à un collègue pour 2 semaines *(athlètes club uniquement)*.
- **Supervision** : l'Owner du club veut suivre la charge globale de tous les athlètes du club sans modifier les programmes → accès "lecture" permanent.
- **Co-coaching** : deux coachs suivent ensemble un groupe d'élite → tous deux en "écriture" sur les mêmes athlètes club.
- **Spécialisation** : un coach spécialiste trail intervient ponctuellement sur un athlète route qui prépare un trail → permission "commentaire" ciblée.
- **Clientèle personnelle** : un coach principal suit aussi des athlètes en privé (hors structure du club) → ces athlètes n'apparaissent jamais dans les vues club, ni pour l'Owner.

---

## 3. ARCHITECTURE BASE DE DONNÉES — SCHÉMA SQL COMPLET

```sql
-- ============================================================
-- EXTENSIONS
-- ============================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm"; -- recherche full-text

-- ============================================================
-- ENUM TYPES
-- ============================================================
CREATE TYPE user_role AS ENUM ('coach', 'athlete', 'admin');
CREATE TYPE club_role AS ENUM ('owner', 'coach_principal', 'coach_assistant');
CREATE TYPE permission_level AS ENUM ('read', 'comment', 'write');
CREATE TYPE athlete_ownership_type AS ENUM ('private', 'club');
CREATE TYPE discipline AS ENUM ('route', 'trail');
CREATE TYPE objective_type AS ENUM ('A', 'B', 'C');
CREATE TYPE form_status AS ENUM ('green', 'orange', 'red');
CREATE TYPE sync_provider AS ENUM ('strava', 'garmin', 'coros', 'polar', 'suunto');
CREATE TYPE attachment_type AS ENUM ('pdf', 'image', 'video', 'link');
CREATE TYPE intensity_domain AS ENUM ('domain_1', 'domain_2', 'domain_3');
CREATE TYPE test_type AS ENUM ('lactate', 'critical_speed');
CREATE TYPE notification_type AS ENUM (
  'athlete_unavailable', 'session_moved', 'new_comment',
  'high_fatigue', 'high_pain', 'activity_synced'
);

-- ============================================================
-- PROFILES (extends Supabase auth.users)
-- ============================================================
CREATE TABLE profiles (
  id              UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  role            user_role NOT NULL,
  first_name      TEXT NOT NULL,
  last_name       TEXT NOT NULL,
  email           TEXT NOT NULL,
  avatar_url      TEXT,
  created_at      TIMESTAMPTZ DEFAULT NOW(),
  updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- CLUBS (structures regroupant plusieurs coachs)
-- ============================================================
CREATE TABLE clubs (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name        TEXT NOT NULL,
  logo_url    TEXT,
  owner_id    UUID NOT NULL REFERENCES profiles(id),
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  updated_at  TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- CLUB MEMBERS (coachs rattachés à un club + leur rôle)
-- ============================================================
CREATE TABLE club_members (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  club_id     UUID NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
  coach_id    UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  club_role   club_role NOT NULL DEFAULT 'coach_assistant',
  invited_by  UUID REFERENCES profiles(id),
  joined_at   TIMESTAMPTZ DEFAULT NOW(),
  active      BOOLEAN DEFAULT TRUE,
  UNIQUE(club_id, coach_id)
);

-- ============================================================
-- ATHLETE CLUB MEMBERSHIP (lien optionnel athlète ↔ club)
-- Un athlète SANS ligne ici est un athlète "privé" du coach :
-- invisible aux autres coachs du club, quelles que soient
-- les permissions. Une ligne ici = athlète "club" potentiellement
-- partageable selon athlete_coach_permissions.
-- ============================================================
CREATE TABLE athlete_club_membership (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  club_id     UUID NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
  athlete_id  UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  active      BOOLEAN DEFAULT TRUE,
  joined_at   TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(club_id, athlete_id)
);

-- ============================================================
-- COACH → ATHLETE RELATIONSHIPS
-- coach_id = coach référent (responsable principal de la programmation)
-- club_id NULL = athlète privé, rattaché uniquement à ce coach,
--                jamais visible par les autres membres du club.
-- club_id renseigné = athlète "club", potentiellement visible par
--                les autres coachs selon athlete_coach_permissions
--                et le rôle club (Owner / coach principal).
-- ============================================================
CREATE TABLE coach_athlete_relations (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  club_id     UUID REFERENCES clubs(id),  -- NULL = athlète privé
  coach_id    UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  athlete_id  UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  is_referent BOOLEAN DEFAULT TRUE,  -- coach référent principal
  is_private  BOOLEAN GENERATED ALWAYS AS (club_id IS NULL) STORED,
  active      BOOLEAN DEFAULT TRUE,
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(coach_id, athlete_id)
);

-- ============================================================
-- ATHLETE COACH PERMISSIONS (accès des coachs non-référents)
-- Permet à un coach assistant de voir/commenter/modifier un
-- athlète dont il n'est pas le référent (ex: remplacement,
-- coaching collégial, supervision).
-- ============================================================
CREATE TABLE athlete_coach_permissions (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  athlete_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  coach_id        UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  permission      permission_level NOT NULL DEFAULT 'read',
  granted_by      UUID REFERENCES profiles(id),
  granted_at      TIMESTAMPTZ DEFAULT NOW(),
  expires_at      TIMESTAMPTZ,  -- NULL = permanent, sinon ex: remplacement temporaire
  UNIQUE(athlete_id, coach_id)
);

-- ============================================================
-- ATHLETE PROFILES
-- ============================================================
CREATE TABLE athlete_profiles (
  id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  athlete_id                  UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  discipline                  discipline NOT NULL DEFAULT 'route',
  max_sessions_per_week       INTEGER,
  rest_days                   INTEGER[], -- 0=Sun, 1=Mon ... 6=Sat
  unavailable_days_recurring  INTEGER[],
  preferred_long_run_day      INTEGER,
  notes                       TEXT,
  -- Physio Profile
  vc_ms                       NUMERIC(5,2),   -- vitesse critique m/s
  lt1_ms                      NUMERIC(5,2),   -- LT1 m/s
  lt2_ms                      NUMERIC(5,2),   -- LT2 m/s
  fc_max                      INTEGER,
  fc_lt1                      INTEGER,
  fc_lt2                      INTEGER,
  -- Intensity domain thresholds (configurable)
  vc_domain1_pct              NUMERIC(5,2) DEFAULT 90,
  vc_domain2_pct              NUMERIC(5,2) DEFAULT 100,
  fc_domain1_pct              NUMERIC(5,2) DEFAULT 80,
  fc_domain2_pct              NUMERIC(5,2) DEFAULT 90,
  -- VDOT (calculé automatiquement)
  vdot                        NUMERIC(5,2),
  updated_at                  TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- GROUPES D'ATHLÈTES (catégories personnalisées par le coach)
-- Ex : "Élite trail", "Prépa marathon automne", "Réathlé",
-- "Débutants". Un athlète peut appartenir à plusieurs groupes.
-- ============================================================
CREATE TABLE athlete_groups (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  coach_id    UUID REFERENCES profiles(id),
  club_id     UUID REFERENCES clubs(id),       -- groupe partagé au niveau club si renseigné
  name        TEXT NOT NULL,
  color       TEXT DEFAULT '#2DD4BF',          -- couleur d'affichage (badge)
  icon        TEXT,                            -- emoji optionnel
  description TEXT,
  sort_order  INTEGER DEFAULT 0,
  is_archived BOOLEAN DEFAULT FALSE,
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Appartenance (many-to-many athlète ↔ groupe)
CREATE TABLE athlete_group_members (
  group_id    UUID NOT NULL REFERENCES athlete_groups(id) ON DELETE CASCADE,
  athlete_id  UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  added_at    TIMESTAMPTZ DEFAULT NOW(),
  PRIMARY KEY (group_id, athlete_id)
);
CREATE INDEX athlete_group_members_athlete_idx ON athlete_group_members(athlete_id);

-- ============================================================
-- ATHLETE PERFORMANCES (pour calcul VDOT)
-- ============================================================
CREATE TABLE athlete_performances (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  athlete_id  UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  distance    TEXT NOT NULL, -- '800m','1500m','3000m','5km','10km','15km','semi','marathon','trail_court','trail_long'
  time_seconds INTEGER NOT NULL,
  date_set    DATE,
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- VDOT PACES (calculées automatiquement, mise à jour par trigger)
-- ============================================================
CREATE TABLE athlete_vdot_paces (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  athlete_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  vdot            NUMERIC(5,2),
  pace_800m_s     INTEGER, -- secondes/km
  pace_1500m_s    INTEGER,
  pace_3000m_s    INTEGER,
  pace_5km_s      INTEGER,
  pace_10km_s     INTEGER,
  pace_15km_s     INTEGER,
  pace_semi_s     INTEGER,
  pace_marathon_s INTEGER,
  updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- OBJECTIVES
-- ============================================================
CREATE TABLE objectives (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  athlete_id  UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  coach_id    UUID NOT NULL REFERENCES profiles(id),
  type        objective_type NOT NULL,
  name        TEXT NOT NULL,
  date        DATE NOT NULL,
  distance    TEXT,
  discipline  discipline,
  notes       TEXT,
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- SESSION LIBRARY (bibliothèque du coach)
-- ============================================================
CREATE TABLE session_categories (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  coach_id    UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  discipline  TEXT, -- null = toutes
  name        TEXT NOT NULL,
  parent_id   UUID REFERENCES session_categories(id), -- hiérarchie libre
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE sessions_library (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  coach_id        UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  name            TEXT NOT NULL,
  category_id     UUID REFERENCES session_categories(id),
  tags            TEXT[],
  is_favorite     BOOLEAN DEFAULT FALSE,
  is_archived     BOOLEAN DEFAULT FALSE,
  use_count       INTEGER DEFAULT 0,
  last_used_at    TIMESTAMPTZ,
  search_vector   TSVECTOR,
  -- Structure
  warmup_content  JSONB, -- [{type:'run', duration_min:10, ...}]
  main_content    JSONB, -- blocs structurés
  cooldown_content JSONB,
  coach_notes     TEXT,
  created_at      TIMESTAMPTZ DEFAULT NOW(),
  updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Index full-text sur la bibliothèque
CREATE INDEX sessions_library_search_idx ON sessions_library USING GIN(search_vector);
CREATE INDEX sessions_library_tags_idx ON sessions_library USING GIN(tags);

-- ============================================================
-- SESSION BLOCKS (structure interne d'une séance)
-- ============================================================
-- Stocké en JSONB dans sessions_library.main_content
-- Structure d'un bloc :
-- {
--   "id": "uuid",
--   "type": "interval" | "tempo" | "easy" | "recovery",
--   "reps": 6,
--   "distance_m": 1000,
--   "duration_s": null,
--   "recovery": { "type": "jog", "duration_s": 90 },
--   "prescription": {
--     "ref_type": "pct_lt2" | "pct_vc" | "pct_pace_5km" | ...,
--     "min_pct": 98,
--     "max_pct": 102
--   }
-- }

-- ============================================================
-- CYCLES LIBRARY
-- ============================================================
CREATE TABLE cycles_library (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  coach_id    UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  name        TEXT NOT NULL,
  weeks       INTEGER NOT NULL,
  description TEXT,
  structure   JSONB, -- [{week:1, sessions:[session_id...]}]
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- SCHEDULED SESSIONS (calendrier athlète)
-- ============================================================
CREATE TABLE scheduled_sessions (
  id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  athlete_id          UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  coach_id            UUID NOT NULL REFERENCES profiles(id),
  session_library_id  UUID REFERENCES sessions_library(id),
  -- Snapshot de la séance au moment de la prescription
  session_snapshot    JSONB NOT NULL,
  -- Allures calculées pour cet athlète
  calculated_paces    JSONB,
  scheduled_date      DATE NOT NULL,
  original_date       DATE,  -- date initiale avant déplacement
  moved_by_athlete    BOOLEAN DEFAULT FALSE,
  objective_id        UUID REFERENCES objectives(id),
  coach_notes         TEXT,
  -- Retour athlète
  completed           BOOLEAN DEFAULT FALSE,
  completed_at        TIMESTAMPTZ,
  athlete_rpe         INTEGER CHECK (athlete_rpe BETWEEN 1 AND 10),
  athlete_fatigue     INTEGER CHECK (athlete_fatigue BETWEEN 1 AND 10),
  athlete_pain        INTEGER CHECK (athlete_pain BETWEEN 0 AND 10),
  athlete_comment     TEXT,
  -- Activité synchronisée
  synced_activity_id  UUID REFERENCES synced_activities(id),
  created_at          TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- ATHLETE UNAVAILABILITIES
-- ============================================================
CREATE TABLE athlete_unavailabilities (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  athlete_id  UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  date_from   DATE NOT NULL,
  date_to     DATE NOT NULL,
  reason      TEXT,
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- SYNCED ACTIVITIES (depuis Strava, Garmin, etc.)
-- ============================================================
CREATE TABLE synced_activities (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  athlete_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  provider        sync_provider NOT NULL,
  external_id     TEXT NOT NULL,
  activity_date   TIMESTAMPTZ NOT NULL,
  distance_m      NUMERIC(10,2),
  duration_s      INTEGER,
  avg_pace_s      INTEGER,  -- sec/km
  avg_hr          INTEGER,
  elevation_m     NUMERIC(8,2),
  gps_data        JSONB,    -- polyline ou points GPS
  laps            JSONB,
  hr_zones        JSONB,
  raw_data        JSONB,
  created_at      TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(provider, external_id)
);

-- ============================================================
-- SYNC TOKENS (OAuth wearables)
-- ============================================================
CREATE TABLE sync_tokens (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  athlete_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  provider        sync_provider NOT NULL,
  access_token    TEXT,
  refresh_token   TEXT,
  expires_at      TIMESTAMPTZ,
  scope           TEXT,
  created_at      TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(athlete_id, provider)
);

-- ============================================================
-- LACTATE TESTS
-- ============================================================
CREATE TABLE lactate_tests (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  athlete_id  UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  coach_id    UUID NOT NULL REFERENCES profiles(id),
  test_type   test_type NOT NULL,
  test_date   DATE NOT NULL,
  notes       TEXT,
  -- Résultats extraits
  lt1_ms      NUMERIC(5,2),
  lt2_ms      NUMERIC(5,2),
  vc_ms       NUMERIC(5,2),
  fc_lt1      INTEGER,
  fc_lt2      INTEGER,
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE lactate_test_steps (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  test_id     UUID NOT NULL REFERENCES lactate_tests(id) ON DELETE CASCADE,
  step_order  INTEGER NOT NULL,
  speed_ms    NUMERIC(5,2) NOT NULL,
  hr          INTEGER,
  lactate     NUMERIC(5,2),
  rpe         INTEGER CHECK (rpe BETWEEN 1 AND 10),
  duration_s  INTEGER
);

-- ============================================================
-- MESSAGING
-- ============================================================
CREATE TABLE conversations (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  coach_id      UUID NOT NULL REFERENCES profiles(id),
  athlete_id    UUID NOT NULL REFERENCES profiles(id),
  session_id    UUID REFERENCES scheduled_sessions(id),
  objective_id  UUID REFERENCES objectives(id),
  created_at    TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(coach_id, athlete_id)
);

CREATE TABLE messages (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  sender_id       UUID NOT NULL REFERENCES profiles(id),
  content         TEXT,
  is_pinned       BOOLEAN DEFAULT FALSE,
  read_at         TIMESTAMPTZ,
  created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE message_attachments (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  message_id  UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  type        attachment_type NOT NULL,
  url         TEXT NOT NULL,
  file_name   TEXT,
  file_size   INTEGER
);

-- ============================================================
-- NOTIFICATIONS
-- ============================================================
CREATE TABLE notifications (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id     UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  type        notification_type NOT NULL,
  title       TEXT NOT NULL,
  body        TEXT,
  data        JSONB,
  read        BOOLEAN DEFAULT FALSE,
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- ATTACHMENTS (séances, objectifs, fiche athlète)
-- ============================================================
CREATE TABLE attachments (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  owner_id    UUID NOT NULL REFERENCES profiles(id),
  entity_type TEXT NOT NULL, -- 'session' | 'objective' | 'athlete_profile' | 'message'
  entity_id   UUID NOT NULL,
  type        attachment_type NOT NULL,
  url         TEXT NOT NULL,
  file_name   TEXT,
  file_size   INTEGER,
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- TRAINING LOAD (calculée et mise en cache)
-- ============================================================
CREATE TABLE training_load_cache (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  athlete_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  date            DATE NOT NULL,
  acute_load_7d   NUMERIC(8,2),   -- ATL
  chronic_load_28d NUMERIC(8,2),  -- CTL
  ratio           NUMERIC(5,2),
  domain1_pct_7d  NUMERIC(5,2),
  domain2_pct_7d  NUMERIC(5,2),
  domain3_pct_7d  NUMERIC(5,2),
  domain1_pct_28d NUMERIC(5,2),
  domain2_pct_28d NUMERIC(5,2),
  domain3_pct_28d NUMERIC(5,2),
  updated_at      TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(athlete_id, date)
);

-- ============================================================
-- ════════════════  MODULE PRÉPARATION PHYSIQUE  ════════════
-- ============================================================

-- ── ENUMS spécifiques au module force ──────────────────────
CREATE TYPE exercise_category AS ENUM (
  'force_max','hypertrophie','puissance','pliometrie','isometrie',
  'endurance_musculaire','gainage','mobilite','reathletisation','prevention'
);
CREATE TYPE muscle_group AS ENUM (
  'quadriceps','ischios','mollets','fessiers','tronc','haut_du_corps',
  'pied_cheville','hanche','dos','epaule'
);
CREATE TYPE equipment_type AS ENUM (
  'poids_du_corps','halteres','kettlebell','barre','machine','elastique',
  'medecine_ball','box','trx','poulie','leg_extension','leg_curl','presse','autre'
);
CREATE TYPE block_format AS ENUM (
  'classique','emom','amrap','for_time','circuit','isometrie','pliometrie'
);
CREATE TYPE charge_ref_type AS ENUM (
  'kg_fixe','kg_range','pct_rm','pct_rm_range','rm_cible','rm_estime'
);
CREATE TYPE effort_ref_type AS ENUM ('rpe','rpe_range','rir','rir_range');
CREATE TYPE athlete_level AS ENUM ('debutant','intermediaire','avance');
CREATE TYPE rm_formula AS ENUM ('epley','brzycki','rir_based','nuzzo');

-- Type de série : structure de l'effort à l'intérieur de l'exercice
CREATE TYPE set_type AS ENUM (
  'standard',      -- séries droites classiques
  'drop_set',      -- charge décroissante enchaînée sans repos
  'super_set',     -- 2+ exercices appariés alternés (A1/A2)
  'myo_reps',      -- série d'activation + mini-séries à micro-repos
  'cluster',       -- reps fractionnées avec repos intra-série
  'iso_overcoming',-- isométrie : pousser/tirer contre résistance fixe
  'iso_yielding'   -- isométrie : retenir une charge le plus longtemps
);

-- ── CATÉGORIES D'EXERCICES (hiérarchie libre) ──────────────
CREATE TABLE pp_exercise_categories (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  coach_id    UUID REFERENCES profiles(id),
  club_id     UUID REFERENCES clubs(id),
  name        TEXT NOT NULL,
  parent_id   UUID REFERENCES pp_exercise_categories(id),
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- ── BIBLIOTHÈQUE D'EXERCICES ───────────────────────────────
CREATE TABLE pp_exercises (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  coach_id          UUID REFERENCES profiles(id),
  club_id           UUID REFERENCES clubs(id),
  name              TEXT NOT NULL,
  category          exercise_category NOT NULL,
  muscle_groups     muscle_group[] NOT NULL,
  equipment         equipment_type[] NOT NULL,
  level             athlete_level DEFAULT 'intermediaire',
  objective         TEXT,                    -- libre : "force", "explosivité"…
  video_url         TEXT,
  image_url         TEXT,
  technical_notes   TEXT,
  instructions      TEXT,
  contraindications TEXT,
  progression_id    UUID REFERENCES pp_exercises(id), -- variante plus difficile
  regression_id     UUID REFERENCES pp_exercises(id), -- variante plus facile
  is_favorite       BOOLEAN DEFAULT FALSE,
  is_archived       BOOLEAN DEFAULT FALSE,
  use_count         INTEGER DEFAULT 0,
  search_vector     TSVECTOR,
  created_at        TIMESTAMPTZ DEFAULT NOW(),
  updated_at        TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX pp_exercises_search_idx ON pp_exercises USING GIN(search_vector);
CREATE INDEX pp_exercises_muscle_idx ON pp_exercises USING GIN(muscle_groups);

-- Variantes (many-to-many, distinct de progression/régression)
CREATE TABLE pp_exercise_variants (
  exercise_id  UUID REFERENCES pp_exercises(id) ON DELETE CASCADE,
  variant_id   UUID REFERENCES pp_exercises(id) ON DELETE CASCADE,
  PRIMARY KEY (exercise_id, variant_id)
);

-- ── SÉANCES DE PRÉPARATION PHYSIQUE (bibliothèque) ─────────
CREATE TABLE strength_sessions (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  coach_id      UUID REFERENCES profiles(id),
  club_id       UUID REFERENCES clubs(id),
  name          TEXT NOT NULL,
  category_id   UUID REFERENCES pp_exercise_categories(id),
  tags          TEXT[],
  is_favorite   BOOLEAN DEFAULT FALSE,
  is_archived   BOOLEAN DEFAULT FALSE,
  use_count     INTEGER DEFAULT 0,
  last_used_at  TIMESTAMPTZ,
  notes         TEXT,
  created_at    TIMESTAMPTZ DEFAULT NOW(),
  updated_at    TIMESTAMPTZ DEFAULT NOW()
);

-- ── BLOCS DE SÉANCE (échauffement/activation/principal/…) ──
CREATE TABLE strength_session_blocks (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  session_id    UUID NOT NULL REFERENCES strength_sessions(id) ON DELETE CASCADE,
  block_type    TEXT NOT NULL,  -- 'echauffement'|'activation'|'principal'|'accessoire'|'calme'
  block_order   INTEGER NOT NULL,
  format        block_format DEFAULT 'classique',
  -- Paramètres spécifiques au format (EMOM/AMRAP/Circuit)
  duration_sec  INTEGER,        -- EMOM/AMRAP : durée totale
  rounds        INTEGER,        -- Circuit : nb de tours
  work_sec      INTEGER,        -- Circuit : temps travail
  rest_sec      INTEGER,        -- Circuit : temps repos entre exercices
  created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- ── EXERCICE DANS UN BLOC + PRESCRIPTION COMPLÈTE ──────────
CREATE TABLE strength_session_exercises (
  id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  block_id            UUID NOT NULL REFERENCES strength_session_blocks(id) ON DELETE CASCADE,
  exercise_id         UUID NOT NULL REFERENCES pp_exercises(id),
  exercise_order      INTEGER NOT NULL,
  emom_minute         INTEGER,           -- si format EMOM : à quelle minute

  -- TYPE DE SÉRIE (structure de l'effort)
  set_type            set_type DEFAULT 'standard',

  -- VOLUME
  sets                INTEGER,
  reps_fixed          INTEGER,
  reps_min            INTEGER,
  reps_max            INTEGER,
  duration_sec        INTEGER,           -- isométrie
  plyo_contacts       INTEGER,           -- pliométrie : nb de contacts

  -- CHARGE
  charge_ref_type     charge_ref_type,
  charge_kg_min       NUMERIC(6,2),
  charge_kg_max       NUMERIC(6,2),
  charge_pct_rm_min   NUMERIC(5,2),
  charge_pct_rm_max   NUMERIC(5,2),

  -- EFFORT
  effort_ref_type     effort_ref_type,
  rpe_min             NUMERIC(3,1),
  rpe_max             NUMERIC(3,1),
  rir_min             INTEGER,
  rir_max             INTEGER,

  -- TEMPO ("3-1-X-1" : excentrique-pause bas-concentrique-pause haut)
  tempo               TEXT,

  -- REPOS
  rest_sec_min        INTEGER,
  rest_sec_max        INTEGER,

  -- ════ CONFIG SPÉCIFIQUE PAR TYPE DE SÉRIE (JSONB) ════
  -- La structure dépend de set_type :
  --
  -- drop_set      : { "drops": [{"pct_drop":20,"reps":"AMRAP"},{"pct_drop":20,"reps":"AMRAP"}] }
  --                 (charge de départ = charge principale, puis -20% puis -20%)
  -- super_set     : { "paired_exercise_ids":[uuid...], "rest_between_pairs_sec":90,
  --                   "label":"A" }   -- A1/A2 partagent le même label
  -- myo_reps      : { "activation_reps":15, "mini_sets":4, "mini_reps":3,
  --                   "micro_rest_sec":15 }
  -- cluster       : { "cluster_reps":[2,2,2], "intra_rest_sec":15 }
  --                 (ex : 3×(2+2+2) avec 15s de repos intra-série)
  -- iso_overcoming: { "push_sec":6, "intent":"max", "reps":5, "rest_sec":10,
  --                   "joint_angle_deg":90 }  -- pousser contre résistance fixe
  -- iso_yielding  : { "hold_sec":30, "load_pct_rm":70, "joint_angle_deg":90 }
  --                 (retenir la charge le plus longtemps possible)
  set_config          JSONB,

  -- RÉATHLÉTISATION
  max_pain_allowed    INTEGER CHECK (max_pain_allowed BETWEEN 0 AND 10),

  coach_notes         TEXT,
  created_at          TIMESTAMPTZ DEFAULT NOW()
);

-- ── SÉANCES FORCE PLANIFIÉES (calendrier athlète) ──────────
-- Miroir de scheduled_sessions (course) pour le module force.
CREATE TABLE scheduled_strength_sessions (
  id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  athlete_id          UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  coach_id            UUID NOT NULL REFERENCES profiles(id),
  strength_session_id UUID REFERENCES strength_sessions(id),
  session_snapshot    JSONB NOT NULL,     -- copie figée au moment de la prescription
  scheduled_date      DATE NOT NULL,
  original_date       DATE,
  moved_by_athlete    BOOLEAN DEFAULT FALSE,
  cycle_id            UUID REFERENCES strength_cycles(id),
  -- quels champs demander à l'athlète (adaptatif selon niveau)
  required_fields     JSONB DEFAULT '{"charge":true,"reps":true,"rpe":false,"rir":false,"pain":false,"comment":true}',
  completed           BOOLEAN DEFAULT FALSE,
  completed_at        TIMESTAMPTZ,
  session_rpe         NUMERIC(3,1),       -- RPE séance globale
  session_fatigue     INTEGER CHECK (session_fatigue BETWEEN 1 AND 10),
  session_pain        INTEGER CHECK (session_pain BETWEEN 0 AND 10),
  session_comment     TEXT,
  created_at          TIMESTAMPTZ DEFAULT NOW()
);

-- ── RETOUR ATHLÈTE PAR EXERCICE RÉALISÉ ────────────────────
CREATE TABLE strength_results (
  id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  scheduled_session_id    UUID NOT NULL REFERENCES scheduled_strength_sessions(id) ON DELETE CASCADE,
  session_exercise_id     UUID REFERENCES strength_session_exercises(id),
  athlete_id              UUID NOT NULL REFERENCES profiles(id),
  exercise_id             UUID NOT NULL REFERENCES pp_exercises(id),
  set_number              INTEGER NOT NULL,
  charge_kg               NUMERIC(6,2),
  reps_done               INTEGER,
  duration_sec_done       INTEGER,        -- isométrie réalisée
  rpe_done                NUMERIC(3,1),
  rir_done                INTEGER,
  pain                    INTEGER CHECK (pain BETWEEN 0 AND 10),
  comment                 TEXT,
  created_at              TIMESTAMPTZ DEFAULT NOW()
);

-- ── HISTORIQUE DU e1RM CALCULÉ (par exercice et athlète) ───
CREATE TABLE estimated_1rm (
  id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  athlete_id       UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  exercise_id      UUID NOT NULL REFERENCES pp_exercises(id),
  source_result_id UUID REFERENCES strength_results(id),
  charge_kg        NUMERIC(6,2) NOT NULL,
  reps             INTEGER NOT NULL,
  rpe_or_rir       TEXT,             -- ex "RIR2" ou "RPE8"
  formula_used     rm_formula NOT NULL,
  e1rm_kg          NUMERIC(6,2) NOT NULL,
  calculated_at    TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX e1rm_athlete_exercise_idx ON estimated_1rm(athlete_id, exercise_id, calculated_at DESC);

-- ── CACHE HISTORIQUE PAR EXERCICE (dashboard rapide) ───────
CREATE TABLE athlete_exercise_history (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  athlete_id        UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  exercise_id       UUID NOT NULL REFERENCES pp_exercises(id),
  last_charge_kg    NUMERIC(6,2),
  last_session_date DATE,
  best_e1rm_kg      NUMERIC(6,2),
  best_e1rm_date    DATE,
  best_volume_kg    NUMERIC(8,2),     -- charge × reps × séries cumulé
  trend_pct_8w      NUMERIC(5,2),     -- progression e1RM sur 8 semaines
  last_pain         INTEGER,
  updated_at        TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(athlete_id, exercise_id)
);

-- ── PROFIL 1RM (RM testé/déclaré, base de calcul %RM) ──────
CREATE TABLE athlete_1rm_profile (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  athlete_id  UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  exercise_id UUID NOT NULL REFERENCES pp_exercises(id),
  rm_kg       NUMERIC(6,2) NOT NULL,
  source      TEXT DEFAULT 'estimated', -- 'estimated' | 'tested' | 'manual'
  updated_at  TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(athlete_id, exercise_id)
);

-- ── PROTOCOLES DE TEST 1RM ─────────────────────────────────
-- Trace les tests de force réalisés (vrai 1RM, rep-test, AMRAP)
-- qui INITIALISENT et METTENT À JOUR le athlete_1rm_profile.
CREATE TYPE rm_test_protocol AS ENUM (
  'true_1rm',     -- 1 répétition maximale réelle
  'rep_test_3_5', -- 3-5RM puis extrapolation
  'amrap_test',   -- AMRAP à charge fixe → estimation
  'iso_mvc'       -- contraction isométrique max (dynamomètre)
);
CREATE TABLE strength_tests (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  athlete_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  coach_id        UUID REFERENCES profiles(id),
  exercise_id     UUID NOT NULL REFERENCES pp_exercises(id),
  protocol        rm_test_protocol NOT NULL,
  test_date       DATE NOT NULL,
  -- données brutes du test
  charge_kg       NUMERIC(6,2),
  reps_done       INTEGER,
  rpe             NUMERIC(3,1),
  formula_used    rm_formula,
  -- résultat
  result_1rm_kg   NUMERIC(6,2) NOT NULL,
  notes           TEXT,
  -- déclenche la mise à jour du profil
  applied_to_profile BOOLEAN DEFAULT TRUE,
  created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX strength_tests_idx ON strength_tests(athlete_id, exercise_id, test_date DESC);

-- ── VIDÉO : hors-périmètre V1 ──────────────────────────────
-- L'analyse vidéo native (upload + annotations) n'est PAS
-- implémentée en V1 : les athlètes envoient leurs vidéos au coach
-- par WhatsApp. Seul un lien externe (YouTube/Vimeo) de vidéo de
-- DÉMONSTRATION d'exercice est stocké, dans pp_exercises.video_url.
-- Réintégration possible en V2 si le besoin se confirme.

-- ── CYCLES DE PRÉPARATION PHYSIQUE ─────────────────────────
CREATE TABLE strength_cycles (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  coach_id      UUID REFERENCES profiles(id),
  club_id       UUID REFERENCES clubs(id),
  name          TEXT NOT NULL,           -- "Force max 4 semaines", "LCA phase force"
  weeks         INTEGER NOT NULL,
  objective     TEXT,
  description   TEXT,
  -- structure : [{week:1, sessions:[strength_session_id…], charge_pct_adjustment:0}]
  structure     JSONB NOT NULL,
  created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- ── COMMENTAIRES (génériques, liés aux entités force) ──────
-- entity_type = 'scheduled_strength_session' | 'strength_result'
-- attachments réutilise la table générique avec entity_type =
--   'pp_exercise' | 'strength_session' | 'strength_result'
CREATE TABLE strength_comments (
  id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  author_id    UUID NOT NULL REFERENCES profiles(id),
  entity_type  TEXT NOT NULL,
  entity_id    UUID NOT NULL,
  content      TEXT NOT NULL,
  created_at   TIMESTAMPTZ DEFAULT NOW()
);
```

### Note d'intégration — charge d'entraînement unifiée
La charge des séances de préparation physique (`session_rpe × durée`) s'additionne à la charge course dans `training_load_cache`, produisant **un seul score ACWR/monotonie par athlète** toutes modalités confondues. Le calcul de charge force suit la même logique RPE × durée (en minutes) que les séances course, garantissant la cohérence du suivi de charge globale.

### Charge mécanique vs métabolique (UA) — spécifique Préparation Physique
En complément du score global, le module force calcule deux charges distinctes en **Unités Arbitraires**, affichées dans l'onglet Suivi & Analyse :

- **Charge mécanique (UA)** = `Σ (charge_kg × reps × séries × %1RM) / 100`
  Reflète le **stress structurel** (tendons, articulations, os). Un tonnage lourd à haute intensité génère beaucoup de charge mécanique même à faible volume.
- **Charge métabolique (UA)** = `RPE_séance × durée_min` (méthode sRPE de Foster)
  Reflète le **coût énergétique et la fatigue centrale**. Les circuits, l'hypertrophie haute densité et les formats EMOM/AMRAP génèrent beaucoup de charge métabolique.
- **Ratio Méca/Métab** : un ratio élevé = travail lourd peu fatigant (force max) → surveiller les tendons ; un ratio bas = travail métabolique → surveiller la récupération centrale.

```sql
CREATE TABLE strength_load_tracking (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  athlete_id      UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  scheduled_session_id UUID REFERENCES scheduled_strength_sessions(id) ON DELETE CASCADE,
  date            DATE NOT NULL,
  mechanical_load_au NUMERIC(8,2),   -- Σ(charge×reps×séries×%1RM)/100
  metabolic_load_au  NUMERIC(8,2),   -- RPE séance × durée min
  ratio_mech_metab   NUMERIC(5,2),   -- mechanical / metabolic
  session_count   INTEGER DEFAULT 1, -- pour agrégation hebdomadaire
  created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX strength_load_idx ON strength_load_tracking(athlete_id, date DESC);
```

Ces deux charges restent **propres au module Préparation Physique** (elles ne se substituent pas au score ACWR unifié ; elles l'affinent pour le pilotage spécifique de la force).

---

## 4. PARCOURS UTILISATEURS

### 4.1 Parcours Coach — Flux Principal

```
[Connexion]
    │
    ▼
[Dashboard]
 • Vue globale tous athlètes
 • Alertes fatigue/douleur
 • Notifications
    │
    ├──► [Fiche Athlète]
    │     • Profil physio
    │     • Historique performances
    │     • VDOT calculé
    │     └──► [Modifier profil physio]
    │
    ├──► [Calendrier Athlète]
    │     • Vue semaine / mois
    │     • Drag & drop de séances
    │     • Ajout via bibliothèque
    │     └──► [Prescrire une séance]
    │           • Chercher dans bibliothèque
    │           • Ou créer à la volée
    │           • Calculateur automatique allures
    │
    ├──► [Bibliothèque]
    │     • Créer/éditer séances
    │     • Organiser par catégories
    │     └──► [Éditeur de séance]
    │           • Échauffement / Corps / Retour
    │           • Prescription en fourchettes
    │
    ├──► [Tests]
    │     • Saisir test lactate
    │     • Saisir test VC
    │     └──► [Profil Lactate]
    │           • Graphiques courbes
    │           • Historique LT1/LT2/VC
    │
    ├──► [Analyse Athlète]
    │     • État de forme
    │     • Charge aiguë/chronique
    │     • Répartition intensité
    │
    └──► [Messagerie]
          • Échanges athlète
          • Pièces jointes
          • Messages épinglés
```

### 4.2 Parcours Athlète — Flux Principal

```
[Connexion]
    │
    ▼
[Calendrier / Séance du Jour]
    │
    ├──► [Séance du Jour]
    │     • Affichage détaillé
    │     • Allures calculées pour moi
    │     • Échauffement / Corps / Retour
    │     └──► [Renseigner retour]
    │           • RPE | Fatigue | Douleur
    │           • Commentaire libre
    │
    ├──► [Calendrier]
    │     • Mes séances
    │     • Mes objectifs
    │     • Déplacer une séance
    │     └──► [Déclarer indisponibilité]
    │
    └──► [Messages]
          • Échanges avec coach
```

---

## 5. WIREFRAMES — TOUS LES ÉCRANS

### 5.1 Dashboard Coach

```
┌─────────────────────────────────────────────────────────┐
│ DARI Lab Training          🔔 3    [Recherche]          │
├──────────┬──────────────────────────────────────────────┤
│          │  Bonjour, Thomas 👋                          │
│ Dashboard│                                              │
│ Athlètes │  ┌─────────┐  ┌─────────┐  ┌─────────┐     │
│ Calendrier  │ Total   │  │ Route   │  │ Trail   │     │
│ Biblio   │  │   12    │  │    7    │  │    5    │     │
│ Cycles   │  └─────────┘  └─────────┘  └─────────┘     │
│ Messages │                                              │
│ Tests    │  Athlètes Route          Athlètes Trail      │
│ Lactate  │  ┌──────────────────┐  ┌──────────────────┐ │
│ Synchros │  │ ● Forme  Athlète │  │ ● Forme  Athlète │ │
│ Paramèt. │  │ 🟢  M. Dupont   │  │ 🔴  A. Rossi    │ │
│          │  │ 🟡  L. Martin   │  │ 🟢  K. Mbeki    │ │
│ ─────── │  │ 🟢  S. Bernard  │  │ 🟡  C. Roux     │ │
│ + Athlète│  │ 🔴  J. Moreau   │  └──────────────────┘ │
│ + Séance │  └──────────────────┘                       │
│ + Cycle  │                                              │
│ + Test   │                                              │
└──────────┴──────────────────────────────────────────────┘
```

### 5.2 Fiche Athlète

```
┌─────────────────────────────────────────────────────────┐
│ ← Athlètes   Marie Dupont  [Route]  🟢                 │
├────────────────────┬────────────────────────────────────┤
│ INFORMATIONS       │  PROFIL PHYSIO                     │
│                    │                                    │
│ Prénom   Marie     │  VC       4.20 m/s (15.1 km/h)    │
│ Nom      Dupont    │  LT1      3.50 m/s (12.6 km/h)    │
│                    │  LT2      3.90 m/s (14.0 km/h)    │
│ CONTRAINTES        │  FCmax    178 bpm                  │
│                    │  FC LT1   148 bpm                  │
│ Max séances/sem  5 │  FC LT2   163 bpm                  │
│ Repos      Lundi   │                                    │
│ Indisp.    ──      │  VDOT     52.3 ↗                   │
│ Sortie longue Sam  │                                    │
│                    │  ┌─────────────────────────────┐   │
│ Notes              │  │ ALLURES CALCULÉES (VDOT)    │   │
│ Sensible mollet G  │  │ 800m    3:35/km             │   │
│                    │  │ 1500m   3:52/km             │   │
├────────────────────┤  │ 5km     4:07/km             │   │
│ HISTORIQUE SPORTIF │  │ 10km    4:17/km             │   │
│                    │  │ Semi    4:28/km             │   │
│ 5km    19'45"      │  │ Marathon 4:41/km            │   │
│ 10km   41'20"      │  └─────────────────────────────┘   │
│ Semi   1h31'       │                                    │
│ Marathon ──        │  [Modifier profil physio]          │
└────────────────────┴────────────────────────────────────┘
```

### 5.3 Calendrier Coach (vue semaine)

```
┌─────────────────────────────────────────────────────────┐
│ Calendrier — Marie Dupont    < Semaine 24 >  [Mois]    │
├─────┬──────┬──────┬──────┬──────┬──────┬──────┬────────┤
│     │ LUN  │ MAR  │ MER  │ JEU  │ VEN  │ SAM  │ DIM    │
│     │  10  │  11  │  12  │  13  │  14  │  15  │  16    │
├─────┼──────┼──────┼──────┼──────┼──────┼──────┼────────┤
│     │ REPO │      │      │      │      │      │        │
│     │      │[EF]  │      │[VMA] │      │[Long]│        │
│     │      │45min │      │1h10  │      │1h45  │        │
│     │      │Dom1  │      │Dom2-3│      │Dom1  │        │
│     │      │      │      │      │      │      │        │
│     │ ════ │      │  🎯  │      │      │      │        │
│     │      │      │ Obj B│      │      │      │        │
└─────┴──────┴──────┴──────┴──────┴──────┴──────┴────────┘
  [+ Séance] [+ Objectif] [+ Note] [Bibliothèque ↗]
```

### 5.4 Éditeur de Séance

```
┌─────────────────────────────────────────────────────────┐
│ Nouvelle Séance           [Sauvegarder] [Dupliquer]    │
├─────────────────────────────────────────────────────────┤
│ Nom  [VMA 6×1000m ________________]                     │
│ Catégorie  [Vitesse > VMA         ▼]  Tags [+]         │
├─────────────────────────────────────────────────────────┤
│ ÉCHAUFFEMENT                                            │
│  + Bloc  [Course légère  10-15 min  Dom1              ] │
│          Référence: %LT1  Fourchette: [80] – [90] %    │
├─────────────────────────────────────────────────────────┤
│ CORPS DE SÉANCE                                         │
│  + Bloc  Répétitions × Distance / Durée                 │
│  ┌───────────────────────────────────────────────────┐  │
│  │ 6 × 1000m                                        │  │
│  │ Référence: [% allure 5km ▼]                      │  │
│  │ Fourchette: [98] – [103] %                       │  │
│  │ Récupération: Trot 90s                           │  │
│  │ ──────────────────────────────────────────────   │  │
│  │ 📊 CALCULATEUR (Marie Dupont)                    │  │
│  │  Allure cible: 4:01 – 4:16 /km                  │  │
│  │  Vitesse:     14.1 – 14.9 km/h                  │  │
│  │  FC cible:    165 – 172 bpm                      │  │
│  │  RPE estimé:  7 – 8                             │  │
│  │  Durée estimée: 4:01 – 4:16 / répét.            │  │
│  │  Volume estimé: 6.0 km corps                    │  │
│  └───────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────┤
│ RETOUR AU CALME                                         │
│  + Bloc  [Course légère  10 min  Dom1                 ] │
├─────────────────────────────────────────────────────────┤
│ Notes coach  [_________________________________________] │
└─────────────────────────────────────────────────────────┘
```

### 5.5 Profil Lactate

```
┌─────────────────────────────────────────────────────────┐
│ Profil Lactate — Marie Dupont         [+ Nouveau Test] │
├─────────────────────────────────────────────────────────┤
│ Résultats actuels                                       │
│ LT1  3.50 m/s  │  LT2  3.90 m/s  │  VC  4.20 m/s     │
│ FC LT1  148    │  FC LT2  163                          │
├─────────────────────────────────────────────────────────┤
│ Courbe Lactate/Vitesse        [Jan 2025]               │
│                                                         │
│  8 ┤                              ●                    │
│  6 ┤                         ●                         │
│  4 ┤                    ●                              │
│  2 ┤          ●    ●                   LT1  LT2        │
│  1 ┤  ●  ●                              │    │         │
│    └──┬──┬──┬──┬──┬──┬──┬──             │    │         │
│       10 11 12 13 14 15 16 km/h         ↓    ↓         │
│                                                         │
├─────────────────────────────────────────────────────────┤
│ Évolution LT2 (historique)                             │
│  14.5 ┤              ●──●                              │
│  14.0 ┤         ●                                     │
│  13.5 ┤    ●                                          │
│       └──sep──dec──mar──jun                            │
└─────────────────────────────────────────────────────────┘
```

### 5.6 Analyse Athlète

```
┌─────────────────────────────────────────────────────────┐
│ Analyse — Marie Dupont                                  │
├──────────────┬──────────────────────────────────────────┤
│ ÉTAT DE FORME│  CHARGE D'ENTRAÎNEMENT                   │
│              │                                          │
│ 🟢 BON       │  Charge aiguë (7j)    342  pts          │
│              │  Charge chronique(28j) 318  pts          │
│ Fatigue  3/10│  Ratio               1.07  ✅            │
│ Douleur  1/10│                                          │
│              │                                          │
├──────────────┤  RÉPARTITION INTENSITÉ                   │
│              │                                          │
│ Dernière     │       7 jours        28 jours            │
│ séance :     │  Dom1  ████ 72%    ███████ 78%          │
│ il y a 2j    │  Dom2  ██   18%    ██      14%          │
│              │  Dom3  █     10%   █        8%          │
└──────────────┴──────────────────────────────────────────┘
```

### 5.7 App Mobile Athlète — Séance du Jour

```
┌─────────────────────┐
│ DARI Lab     🔔     │
│ Séance du jour      │
├─────────────────────┤
│ Mercredi 11 juin    │
│ VMA 6×1000m         │
│ Durée ~1h10         │
├─────────────────────┤
│ ÉCHAUFFEMENT        │
│ Course légère 15min │
│ < 4:45/km | <148bpm │
├─────────────────────┤
│ CORPS               │
│ 6 × 1000m           │
│ 4:01 – 4:16 /km     │
│ 165 – 172 bpm       │
│ Récup: Trot 90s      │
├─────────────────────┤
│ RETOUR AU CALME     │
│ Course légère 10min │
├─────────────────────┤
│ Notes coach         │
│ Bien t'hydrater     │
├─────────────────────┤
│ [RENSEIGNER RETOUR] │
└─────────────────────┘
```

### 5.8 Retour de Séance (Athlète)

```
┌─────────────────────┐
│ Retour de séance    │
│ VMA 6×1000m         │
├─────────────────────┤
│ RPE          7  /10 │
│ ○○○○○○●○○○         │
│                     │
│ Fatigue      4  /10 │
│ ○○○●○○○○○○         │
│                     │
│ Douleur      0  /10 │
│ ●○○○○○○○○○         │
├─────────────────────┤
│ Commentaire         │
│ ┌─────────────────┐ │
│ │ Séance solide,  │ │
│ │ les 5-6 rép. un │ │
│ │ peu difficiles  │ │
│ └─────────────────┘ │
├─────────────────────┤
│   [ENVOYER]         │
└─────────────────────┘
```

---

## 6. APIs

### 6.1 REST Endpoints (Next.js Route Handlers)

```
AUTH
POST   /api/auth/login
POST   /api/auth/register
POST   /api/auth/invite-athlete

ATHLETES
GET    /api/athletes                    Coach: liste ses athlètes
GET    /api/athletes/:id                Fiche athlète
PUT    /api/athletes/:id/profile        Profil physio
PUT    /api/athletes/:id/performances   Perf historique → déclenche calcul VDOT

VDOT
GET    /api/athletes/:id/vdot           Retourne VDOT + toutes les allures calculées
POST   /api/vdot/calculate              Calcule VDOT depuis une perf (utility)

SESSIONS LIBRARY
GET    /api/library/sessions            Liste + recherche full-text
POST   /api/library/sessions            Créer
PUT    /api/library/sessions/:id        Modifier
DELETE /api/library/sessions/:id        Supprimer
POST   /api/library/sessions/:id/duplicate

CATEGORIES
GET    /api/library/categories          Arbre complet
POST   /api/library/categories
PUT    /api/library/categories/:id
DELETE /api/library/categories/:id

CALENDAR
GET    /api/calendar/:athleteId         Sessions planifiées (range dates)
POST   /api/calendar/schedule           Prescrire une séance
PATCH  /api/calendar/:sessionId/move    Déplacer (athlète ou coach)
POST   /api/calendar/:sessionId/complete  Retour de séance (athlète)

OBJECTIVES
GET    /api/objectives/:athleteId
POST   /api/objectives
PUT    /api/objectives/:id
DELETE /api/objectives/:id

LACTATE TESTS
GET    /api/tests/:athleteId
POST   /api/tests                       Créer test + paliers
GET    /api/tests/:id

ANALYSIS
GET    /api/analysis/:athleteId/load    Charge aiguë/chronique + ratio
GET    /api/analysis/:athleteId/zones   Répartition intensité 7j et 28j

MESSAGING
GET    /api/messages/conversations
GET    /api/messages/:conversationId
POST   /api/messages/:conversationId
PATCH  /api/messages/:messageId/pin

NOTIFICATIONS
GET    /api/notifications
PATCH  /api/notifications/:id/read
PATCH  /api/notifications/read-all

SYNC / WEARABLES
GET    /api/sync/providers              Providers connectés
POST   /api/sync/strava/oauth           OAuth Strava
POST   /api/sync/garmin/oauth
GET    /api/sync/activities/:athleteId
POST   /api/sync/:provider/push-session  Envoyer séance vers montre

PRÉPARATION PHYSIQUE — Exercices
GET    /api/pp/exercises                 Liste filtrable (cat/muscle/matériel/niveau)
POST   /api/pp/exercises                 Créer un exercice
PATCH  /api/pp/exercises/:id             Modifier
DELETE /api/pp/exercises/:id             Archiver
GET    /api/pp/exercises/:id             Détail (vidéo, consignes, progression/régression)

PRÉPARATION PHYSIQUE — Séances
GET    /api/pp/sessions                  Bibliothèque séances force
POST   /api/pp/sessions                  Créer une séance (blocs + exercices + prescription)
PATCH  /api/pp/sessions/:id              Modifier
POST   /api/pp/sessions/:id/duplicate    Dupliquer
POST   /api/pp/sessions/:id/assign       Assigner au calendrier d'un athlète
                                         body: { athleteId, date, required_fields }

PRÉPARATION PHYSIQUE — Calculs & Suivi
GET    /api/pp/athletes/:id/history      Historique par exercice (e1RM, charge, volume)
POST   /api/pp/calc/e1rm                 Calcul e1RM { charge, reps, rpe|rir, formula }
GET    /api/pp/athletes/:id/1rm-profile  RM courants (base de calcul %RM)
POST   /api/pp/results                   Enregistrer retour athlète (par série/exercice)
GET    /api/pp/scheduled/:id/progression Suggestion de progression auto (kg/%RM/volume)

PRÉPARATION PHYSIQUE — Cycles
GET    /api/pp/cycles                    Liste des cycles
POST   /api/pp/cycles                    Créer un cycle (structure JSONB par semaine)
POST   /api/pp/cycles/:id/assign         Assigner un cycle à un athlète
```

### 6.2 Supabase Edge Functions (Webhooks)

```
/functions/strava-webhook     Réception activités Strava
/functions/garmin-webhook     Réception activités Garmin
/functions/coros-webhook
/functions/recalculate-vdot   Trigger après update performances
/functions/update-load-cache  Trigger après séance complétée
/functions/send-notification  Envoi notifications push/email
```

### 6.3 Realtime Subscriptions (Supabase)

```javascript
// Messagerie
supabase
  .channel('messages')
  .on('postgres_changes', { event: 'INSERT', table: 'messages' }, handler)

// Notifications coach
supabase
  .channel('notifications')
  .on('postgres_changes', { event: 'INSERT', table: 'notifications', filter: `user_id=eq.${coachId}` }, handler)

// Retours de séances
supabase
  .channel('session-returns')
  .on('postgres_changes', { event: 'UPDATE', table: 'scheduled_sessions' }, handler)
```

---

## 7. COMPOSANTS REACT

### 7.1 Architecture des Composants

```
src/
├── app/                         # Next.js App Router
│   ├── (coach)/
│   │   ├── dashboard/
│   │   ├── athletes/[id]/
│   │   ├── calendar/[athleteId]/
│   │   ├── library/
│   │   ├── tests/
│   │   ├── lactate/[athleteId]/
│   │   └── messages/
│   └── (athlete)/
│       ├── calendar/
│       ├── today/
│       └── messages/
│
├── components/
│   ├── ui/                      # shadcn base components
│   │
│   ├── dashboard/
│   │   ├── AthleteStatusTable.tsx
│   │   ├── FormStatusBadge.tsx   # 🟢🟡🔴
│   │   └── NotificationPanel.tsx
│   │
│   ├── athlete/
│   │   ├── AthleteCard.tsx
│   │   ├── AthleteProfileForm.tsx
│   │   ├── PhysioProfileSection.tsx
│   │   ├── PerformanceHistoryForm.tsx
│   │   └── VDOTDisplay.tsx
│   │
│   ├── calendar/
│   │   ├── WeekCalendar.tsx
│   │   ├── MonthCalendar.tsx
│   │   ├── SessionCard.tsx
│   │   ├── ObjectiveMarker.tsx
│   │   ├── UnavailabilityBlock.tsx
│   │   └── DragDropProvider.tsx
│   │
│   ├── session/
│   │   ├── SessionEditor.tsx
│   │   ├── SessionBlock.tsx       # Un bloc de séance (intervalle, etc.)
│   │   ├── PrescriptionInput.tsx  # Fourchettes de prescription
│   │   ├── SessionCalculator.tsx  # Calculateur auto allures/FC/RPE
│   │   ├── SessionViewer.tsx      # Lecture athlète
│   │   └── SessionReturnForm.tsx  # Retour RPE/fatigue/douleur
│   │
│   ├── library/
│   │   ├── LibrarySearch.tsx      # Recherche instantanée
│   │   ├── CategoryTree.tsx
│   │   ├── SessionLibraryItem.tsx
│   │   └── LibraryFilters.tsx
│   │
│   ├── lactate/
│   │   ├── LactateChart.tsx       # Recharts: Lactate/Vitesse
│   │   ├── FCVelocityChart.tsx
│   │   ├── LT1HistoryChart.tsx
│   │   ├── LT2HistoryChart.tsx
│   │   ├── VCHistoryChart.tsx
│   │   └── TestStepsTable.tsx
│   │
│   ├── analysis/
│   │   ├── FormStatus.tsx
│   │   ├── TrainingLoadWidget.tsx
│   │   ├── IntensityDistribution.tsx
│   │   └── LoadRatioIndicator.tsx
│   │
│   ├── messaging/
│   │   ├── ConversationList.tsx
│   │   ├── MessageThread.tsx
│   │   ├── MessageInput.tsx       # text + pièces jointes
│   │   └── PinnedMessages.tsx
│   │
│   └── sync/
│       ├── SyncProviderCard.tsx
│       └── ActivityImportList.tsx
│
├── lib/
│   ├── engines/
│   │   ├── vdot.ts               # Moteur VDOT (Daniels)
│   │   ├── sessionCalculator.ts  # Allures, FC, RPE, durée
│   │   ├── loadCalculator.ts     # ATL, CTL, ratio
│   │   └── intensityDomain.ts    # Classification domaine 1/2/3
│   │
│   ├── supabase/
│   │   ├── client.ts
│   │   └── server.ts
│   │
│   └── utils/
│       ├── pace.ts               # Conversions m/s ↔ min/km ↔ km/h
│       └── time.ts
```

### 7.2 Moteur VDOT — Logique Clé

```typescript
// lib/engines/vdot.ts
// Basé sur les tables Jack Daniels "Running Formula"

const VDOT_TABLE: Record<number, Record<string, number>> = {
  // vdot -> paces en sec/km pour chaque distance
  30: { '5km': 420, '10km': 436, 'semi': 462, 'marathon': 478 },
  35: { '5km': 384, '10km': 400, 'semi': 424, 'marathon': 440 },
  // ... jusqu'à 85
  52: { '5km': 247, '10km': 258, 'semi': 269, 'marathon': 285 },
  // etc.
};

export function calculateVDOT(distanceKey: string, timeSeconds: number): number {
  // Formule de Peter Riegel / Jack Daniels
  // T2 = T1 × (D2/D1)^1.06
  // VDOT = -4.6 + 0.182258 * v + 0.000104 * v^2 (v = vitesse m/min)
}

export function getVDOTPaces(vdot: number): AthleteVDOTPaces {
  // Interpolation linéaire dans la table
}

export function getPaceFromPercentage(
  basePaceSecPerKm: number,
  minPct: number,
  maxPct: number
): { minPace: number; maxPace: number } {
  // Plus le % est élevé, plus l'allure est rapide (moins de sec/km)
  // 100% = allure de référence, 105% = plus vite
  return {
    minPace: Math.round(basePaceSecPerKm / (maxPct / 100)),
    maxPace: Math.round(basePaceSecPerKm / (minPct / 100)),
  };
}
```

### 7.3 Calculateur de Séance

```typescript
// lib/engines/sessionCalculator.ts

export function calculateSessionBlock(
  block: SessionBlock,
  athleteProfile: AthletePhysioProfile,
  vdotPaces: AthleteVDOTPaces
): CalculatedBlock {
  const { prescription } = block;
  let basePaceSecPerKm: number;

  switch (prescription.ref_type) {
    case 'pct_lt2':
      basePaceSecPerKm = msToSecPerKm(athleteProfile.lt2_ms);
      break;
    case 'pct_lt1':
      basePaceSecPerKm = msToSecPerKm(athleteProfile.lt1_ms);
      break;
    case 'pct_vc':
      basePaceSecPerKm = msToSecPerKm(athleteProfile.vc_ms);
      break;
    case 'pct_pace_5km':
      basePaceSecPerKm = vdotPaces.pace_5km_s;
      break;
    // ... autres références
  }

  const { minPace, maxPace } = getPaceFromPercentage(
    basePaceSecPerKm,
    prescription.min_pct,
    prescription.max_pct
  );

  return {
    minPaceDisplay: formatPace(minPace),     // "4:01/km"
    maxPaceDisplay: formatPace(maxPace),     // "4:16/km"
    minSpeedKmh: secPerKmToKmh(minPace),    // "14.1 km/h"
    maxSpeedKmh: secPerKmToKmh(maxPace),
    minTargetHR: calculateTargetHR(athleteProfile, minPace),
    maxTargetHR: calculateTargetHR(athleteProfile, maxPace),
    estimatedDurationS: estimateDuration(block, (minPace + maxPace) / 2),
    estimatedDistanceM: estimateDistance(block),
  };
}
```

---

## 8. PRIORITÉS MVP

### Phase 1 — Foundation (6 semaines)
**Objectif : Déployer un coach avec ses athlètes**

| # | Feature | Priorité |
|---|---------|----------|
| 1 | Auth (Coach + invitation Athlète) | 🔴 Critique |
| 2 | Fiche athlète + profil physio | 🔴 Critique |
| 3 | Moteur VDOT (calcul + affichage allures) | 🔴 Critique |
| 4 | Bibliothèque séances (CRUD + catégories) | 🔴 Critique |
| 5 | Éditeur de séance (blocs + prescription fourchettes) | 🔴 Critique |
| 6 | Calendrier coach (vue semaine + planification) | 🔴 Critique |
| 7 | App athlète : Séance du jour + Retour | 🔴 Critique |
| 8 | Dashboard Coach (tableaux Route/Trail + état de forme) | 🔴 Critique |

### Phase 2 — Core Features (4 semaines)
**Objectif : Rendre le produit utilisable au quotidien**

| # | Feature | Priorité |
|---|---------|----------|
| 9  | Calculateur de séance automatique | 🟠 Haute |
| 10 | Calendrier mois + vue athlète | 🟠 Haute |
| 11 | Messagerie Coach↔Athlète | 🟠 Haute |
| 12 | Objectifs (A/B/C) dans calendrier | 🟠 Haute |
| 13 | Saisie tests lactate + paliers | 🟠 Haute |
| 14 | Profil Lactate + graphiques | 🟠 Haute |
| 15 | Notifications (fatigue, douleur, séance déplacée) | 🟠 Haute |

### Phase 3 — Intelligence & Sync (4 semaines)
**Objectif : Différenciation produit**

| # | Feature | Priorité |
|---|---------|----------|
| 16 | Synchronisation Strava | 🟡 Moyenne |
| 17 | Analyse charge aiguë/chronique + ratio | 🟡 Moyenne |
| 18 | Répartition intensité domaines 1/2/3 | 🟡 Moyenne |
| 19 | Copier/coller intelligent (séance, semaine, cycle) | 🟡 Moyenne |
| 20 | Bibliothèque cycles | 🟡 Moyenne |
| 21 | Pièces jointes (séances, messages) | 🟡 Moyenne |
| 22 | Recherche full-text séances depuis calendrier | 🟡 Moyenne |

---

## 9. ROADMAP V1 / V2 / V3

### V1 — MVP Opérationnel (Mois 1–4)

**Périmètre :**
- Coach peut gérer ses athlètes, créer des séances, planifier
- Athlète peut consulter, déplacer une séance, renseigner un retour
- Moteur VDOT opérationnel
- Bibliothèque séances avec recherche
- Messagerie de base
- Synchronisation Strava
- Dashboard Coach avec état de forme
- Profil lactate + graphiques basiques

**Stack cible :** Next.js 14 + Supabase + React Native (Expo)

**KPIs V1 :**
- 10 coachs beta utilisateurs
- 50 athlètes
- NPS > 8

---

### V2 — Intelligence & Différenciation (Mois 5–8)

**Nouvelles fonctionnalités :**

1. **Analyse avancée**
   - Charge aiguë/chronique avec graphique ATL/CTL/Ratio dans le temps
   - Alertes automatiques ratio > 1.3 (risque blessure)
   - Répartition intensité polarisée/pyramidale/seuil (détection auto du modèle suivi)

2. **Synchronisations étendues**
   - Garmin Connect (push séances structurées + pull activités)
   - COROS, Polar, Suunto
   - Matching automatique séance prescrite → activité réelle

3. **Copier/Coller Intelligent**
   - Dupliquer une semaine ou un cycle
   - Transférer vers un autre athlète avec recalcul automatique des allures

4. **Tests améliorés**
   - Test VC (D'/tlim) avec protocole guidé
   - Suggestion automatique de mise à jour LT1/LT2/VC après test

5. **Bibliothèque Cycles**
   - Construction de cycles multi-semaines
   - Application d'un cycle à un athlète avec décalage calendaire

6. **Application Mobile Athlète enrichie**
   - Affichage GPS de la séance (zones)
   - Mode séance live (chrono, affichage allures en temps réel)

**KPIs V2 :**
- 50 coachs
- 300 athlètes
- Churn < 5%/mois

---

### V3 — Scale & Groupes (Mois 9–14)

**Nouvelles fonctionnalités :**

1. **Gestion de groupes**
   - Créer un groupe d'athlètes
   - Prescrire une séance à un groupe (recalcul individuel des allures)
   - Vue calendrier de groupe

2. **Analytics Coach**
   - Tableau de bord agrégé : charge moyenne, état de forme moyen, tendances
   - Comparaison entre athlètes (anonymisée)

3. **IA Assistante (optionnel, module premium)**
   - Suggestion de séances basée sur la charge et l'objectif
   - Détection d'anomalie : athlète sur-entraîné ou sous-entraîné

4. **API Publique**
   - Permettre à des coachs partenaires d'intégrer DARI Lab dans leur workflow

5. **White Label**
   - Permettre à des structures (clubs, fédérations) de déployer leur propre instance

6. **Facturation Stripe**
   - Plans Coach (solo, équipe, entreprise)
   - Facturation athlète optionnelle

**KPIs V3 :**
- 200 coachs
- 2000 athlètes
- ARR > 150k€
- App Store rating > 4.5

---

## ANNEXE — Domaines d'Intensité : Logique de Classification

```typescript
// lib/engines/intensityDomain.ts

export function classifyDomain(
  speedMs: number,
  hrBpm: number,
  athleteProfile: AthletePhysioProfile
): intensity_domain {
  const { lt1_ms, lt2_ms, fc_max, vc_domain1_pct, vc_domain2_pct, fc_domain1_pct, fc_domain2_pct } = athleteProfile;

  // Priorité à la physiologie (LT1/LT2) si disponible
  if (lt1_ms && lt2_ms) {
    if (speedMs < lt1_ms) return 'domain_1';
    if (speedMs <= lt2_ms) return 'domain_2';
    return 'domain_3';
  }

  // Fallback FC
  if (fc_max && hrBpm) {
    const hrPct = (hrBpm / fc_max) * 100;
    if (hrPct < fc_domain1_pct) return 'domain_1';
    if (hrPct <= fc_domain2_pct) return 'domain_2';
    return 'domain_3';
  }

  return 'domain_1'; // défaut conservatif
}
```

---

## ANNEXE — Modèle de Données JSONB : Structure d'un Bloc de Séance

```json
{
  "warmup": [
    {
      "id": "wu-1",
      "type": "run",
      "duration_min": 15,
      "prescription": {
        "ref_type": "pct_lt1",
        "min_pct": 75,
        "max_pct": 88
      }
    }
  ],
  "main": [
    {
      "id": "main-1",
      "type": "intervals",
      "reps": 6,
      "distance_m": 1000,
      "duration_s": null,
      "prescription": {
        "ref_type": "pct_pace_5km",
        "min_pct": 98,
        "max_pct": 103
      },
      "recovery": {
        "type": "jog",
        "duration_s": 90,
        "prescription": {
          "ref_type": "pct_lt1",
          "min_pct": 60,
          "max_pct": 75
        }
      }
    }
  ],
  "cooldown": [
    {
      "id": "cd-1",
      "type": "run",
      "duration_min": 10,
      "prescription": {
        "ref_type": "pct_lt1",
        "min_pct": 60,
        "max_pct": 80
      }
    }
  ]
}
```

---

## ANNEXE — Préparation Physique : Logique de Calcul

### Estimation du 1RM (quatre formules au choix du coach)

**Méthode recommandée par défaut : Nuzzo et al. (2024)** — relation reps→%1RM polynomiale de degré 3, nettement plus précise que les formules linéaires classiques, notamment au-delà de 8 répétitions où Epley/Brzycki divergent. Implémentée d'après l'outil du Pr. Lilian Lacourpaille (Université de Nantes).

```javascript
// ── NUZZO et al. (2024) — RECOMMANDÉE ──
// %1RM en fonction du nombre de reps réalisées (polynôme degré 3)
function nuzzoPct1RM(reps) {
  return -0.0002 * reps**3 + 0.0363 * reps**2 - 2.7814 * reps + 104.9;
}
function nuzzo1RM(weight, reps) {
  return weight * 100 / nuzzoPct1RM(reps);
}
// Ex : 15 kg × 6 reps → %1RM = 89.5% → 1RM = 16.76 kg
// La fonction inverse donne la charge cible pour un %1RM voulu :
function nuzzoChargeForPct(oneRM, pct) {
  return oneRM * pct / 100;
}
// Et la charge pour un nombre de reps cible (table de réps → %1RM) :
function nuzzoChargeForReps(oneRM, targetReps) {
  return oneRM * nuzzoPct1RM(targetReps) / 100;
}

// EPLEY
function epley1RM(weight, reps) {
  return reps === 1 ? weight : weight * (1 + reps / 30);
}

// BRZYCKI
function brzycki1RM(weight, reps) {
  return reps === 1 ? weight : weight * (36 / (37 - reps));
}

// RIR-BASED (table % du 1RM selon reps "réelles" = reps faites + RIR)
const RIR_PCT_TABLE = {
  1:100, 2:95.5, 3:92.2, 4:89.2, 5:86.3, 6:83.7,
  7:81.1, 8:78.6, 9:76.2, 10:73.9, 11:71.6, 12:69.4
};
function rirBased1RM(weight, repsDone, rir) {
  const effectiveReps = repsDone + rir;
  const pct = RIR_PCT_TABLE[Math.min(effectiveReps, 12)] || 65;
  return weight / (pct / 100);
}
```

**Zones de travail dérivées du 1RM (méthode Lacourpaille)**
| Zone | % 1RM |
|------|-------|
| Puissance-vitesse | 30-50 % |
| Puissance-force | 50-70 % |
| Force maximale | > 70 % |
| Hypertrophie (Schoenfeld 2021) | 30-80 % |

**Note** : la méthode multi-tests de Lacourpaille permet d'affiner la courbe individuelle en testant 2 charges (une légère ~12-15RM et une lourde ~4-7RM), ce qui personnalise la relation charge↔reps pour chaque athlète plutôt que d'utiliser la courbe populationnelle.

### Conversion RPE ↔ RIR
```
RIR = 10 − RPE   (échelle force, Borg modifiée)
RPE 10 = RIR 0 (échec) · RPE 8 = RIR 2 · RPE 6 = RIR 4
```

### Calcul de la charge cible (depuis %RM)
```
1RM Squat = 120 kg · Prescription 80-85 % RM
→ charge cible = 96 – 102 kg (arrondi au palier de 2,5 kg)
```

### Progression automatique
Si toutes les séries réalisées **et** RIR > cible **et** douleur ≤ 2 :
proposer +2,5 kg (charge < 40 kg) ou +5 kg, ou +2,5 % en %RM, ou augmentation du volume.

### Alertes coach
- Douleur > 3/10 en réathlétisation → alerte haute
- RPE ≥ 9,5 → alerte moyenne
- RIR 0 alors que 1-2 prescrit → alerte moyenne (échec proche)
- Chute de charge > 15 % vs séance précédente → alerte haute

### Types de série avancés (`set_type` + `set_config`)

| Type | Description | Config JSONB |
|------|-------------|--------------|
| **standard** | Séries droites classiques | — |
| **drop_set** | Charge décroissante enchaînée sans repos jusqu'à l'échec | `drops: [{pct_drop, reps}]` |
| **super_set** | 2+ exercices appariés alternés (A1/A2) sans repos entre eux | `paired_exercise_ids, rest_between_pairs_sec, label` |
| **myo_reps** | Série d'activation longue + mini-séries à micro-repos | `activation_reps, mini_sets, mini_reps, micro_rest_sec` |
| **cluster** | Reps fractionnées avec repos intra-série (ex 3×(2+2+2)/15s) | `cluster_reps:[2,2,2], intra_rest_sec` |
| **iso_overcoming** | Pousser/tirer à intensité max contre une résistance fixe immobile | `push_sec, intent, reps, rest_sec, joint_angle_deg` |
| **iso_yielding** | Retenir une charge donnée le plus longtemps possible | `hold_sec, load_pct_rm, joint_angle_deg` |

**Exemples concrets**
- *Drop set* Leg Extension : 1×10 @100% puis −20% AMRAP puis −20% AMRAP
- *Super set* A1 Squat / A2 Leg Curl : alterner, repos 90s entre paires
- *Myo-reps* : 15 reps d'activation, puis 4×3 avec 15s de micro-repos
- *Cluster* Squat lourd : 3×(2+2+2) à 90% RM, 15s de repos intra-série
- *Iso overcoming* : pousser à fond 6s contre barre bloquée à 90° genou, 5 reps
- *Iso yielding* : tenir 30s un squat à 70% RM à 90° de flexion

Chaque type a sa propre logique d'affichage côté athlète (ex : un drop set montre les paliers de charge successifs, un cluster affiche le décompte intra-série avec minuteur de micro-repos).

### Champs demandés à l'athlète (préréglages par niveau)
| Niveau | charge | reps | RPE | RIR | douleur | commentaire |
|--------|:------:|:----:|:---:|:---:|:-------:|:-----------:|
| Débutant | ✓ | ✓ | — | — | — | ✓ |
| Avancé | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Réathlétisation | ✓ | ✓ | ✓ | — | ✓ | ✓ |

### Roadmap module Préparation Physique
- **MVP** : bibliothèque exercices, séance format Classique, intégration calendrier (drag & drop + calcul charge), retour athlète simple, e1RM Epley
- **V1** : formats EMOM/AMRAP/Circuit/Isométrie/Pliométrie, formules Brzycki + RIR-based, progression auto, alertes, champs adaptatifs par niveau, cycles
- **V2** : analyse force complète, ACWR unifié course+force, cycles spécialisés (LCA/Achille/Hyrox), export PDF programme. Analyse vidéo native (upload + annotations coach) — reportée, remplacée en V1 par l'envoi WhatsApp + lien de démo externe

---

*Document produit par DARI Lab Training — CTO/Product Architecture v1.0*
*Prêt pour développement Sprint 1*
