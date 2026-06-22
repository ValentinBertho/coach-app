// ============================================================
// DARI Lab Training — Types TypeScript partagés
// Source de vérité des contrats de données (front ↔ back).
// À placer dans /packages/types ou /lib/types et importer partout.
// Les noms de champs correspondent EXACTEMENT au schéma SQL.
// ============================================================

// ─────────────────────────────────────────────────────────────
// ENUMS (miroir des types PostgreSQL)
// ─────────────────────────────────────────────────────────────
export type UserRole = 'coach' | 'athlete' | 'admin';
export type ClubRole = 'owner' | 'coach_principal' | 'coach_assistant';
export type PermissionLevel = 'read' | 'comment' | 'write';
export type AthleteOwnershipType = 'private' | 'club';
export type Discipline = 'route' | 'trail';
export type ObjectiveType = 'A' | 'B' | 'C';
export type FormStatus = 'green' | 'orange' | 'red';
export type SyncProvider = 'strava' | 'garmin' | 'coros' | 'polar' | 'suunto';
export type IntensityDomain = 'domain_1' | 'domain_2' | 'domain_3';
export type TestType = 'lactate' | 'critical_speed';

export type ExerciseCategory =
  | 'force_max' | 'hypertrophie' | 'puissance' | 'pliometrie' | 'isometrie'
  | 'endurance_musculaire' | 'gainage' | 'mobilite' | 'reathletisation' | 'prevention';
export type MuscleGroup =
  | 'quadriceps' | 'ischios' | 'mollets' | 'fessiers' | 'tronc'
  | 'haut_du_corps' | 'pied_cheville' | 'hanche' | 'dos' | 'epaule';
export type EquipmentType =
  | 'poids_du_corps' | 'halteres' | 'kettlebell' | 'barre' | 'machine' | 'elastique'
  | 'medecine_ball' | 'box' | 'trx' | 'poulie' | 'leg_extension' | 'leg_curl' | 'presse' | 'autre';
export type BlockFormat =
  | 'classique' | 'emom' | 'amrap' | 'for_time' | 'circuit' | 'isometrie' | 'pliometrie';
export type ChargeRefType =
  | 'kg_fixe' | 'kg_range' | 'pct_rm' | 'pct_rm_range' | 'rm_cible' | 'rm_estime';
export type EffortRefType = 'rpe' | 'rpe_range' | 'rir' | 'rir_range';
export type AthleteLevel = 'debutant' | 'intermediaire' | 'avance';
export type RmFormula = 'epley' | 'brzycki' | 'rir_based' | 'nuzzo';
export type SetType =
  | 'standard' | 'drop_set' | 'super_set' | 'myo_reps'
  | 'cluster' | 'iso_overcoming' | 'iso_yielding';
export type RmTestProtocol = 'true_1rm' | 'rep_test_3_5' | 'amrap_test' | 'iso_mvc';
export type BlockType = 'echauffement' | 'activation' | 'principal' | 'accessoire' | 'calme';

// ─────────────────────────────────────────────────────────────
// IDENTITÉ & CLUB
// ─────────────────────────────────────────────────────────────
export interface Profile {
  id: string;
  role: UserRole;
  first_name: string;
  last_name: string;
  email: string;
  avatar_url: string | null;
  created_at: string;
  updated_at: string;
}

export interface Club {
  id: string;
  name: string;
  logo_url: string | null;
  owner_id: string;
  created_at: string;
  updated_at: string;
}

export interface ClubMember {
  id: string;
  club_id: string;
  coach_id: string;
  club_role: ClubRole;
  invited_by: string | null;
  joined_at: string;
  active: boolean;
}

export interface CoachAthleteRelation {
  id: string;
  club_id: string | null;          // NULL = athlète privé
  coach_id: string;
  athlete_id: string;
  is_referent: boolean;
  ownership_type: AthleteOwnershipType;
  active: boolean;
  created_at: string;
}

export interface AthleteCoachPermission {
  id: string;
  athlete_id: string;
  coach_id: string;
  permission: PermissionLevel;
  granted_by: string | null;
  granted_at: string;
  expires_at: string | null;       // NULL = permanent
}

// ─────────────────────────────────────────────────────────────
// ATHLÈTE
// ─────────────────────────────────────────────────────────────
export interface AthleteProfile {
  id: string;
  athlete_id: string;
  discipline: Discipline;
  max_sessions_per_week: number | null;
  rest_days: number[];             // 0=Dim … 6=Sam
  unavailable_days_recurring: number[];
  preferred_long_run_day: number | null;
  notes: string | null;
  // Physiologie
  vc_ms: number | null;            // vitesse critique m/s
  lt1_ms: number | null;
  lt2_ms: number | null;
  fc_max: number | null;
  fc_lt1: number | null;
  fc_lt2: number | null;
  vc_domain1_pct: number;          // défaut 90
  vc_domain2_pct: number;          // défaut 100
  fc_domain1_pct: number;          // défaut 80
  fc_domain2_pct: number;          // défaut 90
  vdot: number | null;
  updated_at: string;
}

export interface AthleteGroup {
  id: string;
  coach_id: string | null;
  club_id: string | null;          // si renseigné = groupe partagé club
  name: string;
  color: string;                   // hex, défaut #2DD4BF
  icon: string | null;             // emoji
  description: string | null;
  sort_order: number;
  is_archived: boolean;
  created_at: string;
}

export interface AthleteGroupMember {
  group_id: string;
  athlete_id: string;
  added_at: string;
}

// ─────────────────────────────────────────────────────────────
// COURSE — séances & prescription
// ─────────────────────────────────────────────────────────────
/** Une prescription course se fait TOUJOURS en fourchette (min/max). */
export interface CoursePrescription {
  ref_type: 'pct_lt1' | 'pct_lt2' | 'pct_vc' | 'pct_vdot' | 'pct_fcmax' | 'allure_libre';
  min_pct: number | null;
  max_pct: number | null;
  // Allures calculées (dérivées, en secondes par km) — remplies par le moteur
  min_pace_s_per_km?: number;
  max_pace_s_per_km?: number;
}

export interface SessionBlock {
  id: string;
  type: 'warmup' | 'body' | 'cooldown';
  label: string;
  duration_min: number | null;
  reps?: number;                   // pour intervalles
  work?: { duration_s?: number; distance_m?: number; prescription: CoursePrescription };
  recovery?: { duration_s?: number; prescription: CoursePrescription };
  prescription?: CoursePrescription;
}

export interface ScheduledSession {
  id: string;
  athlete_id: string;
  coach_id: string;
  session_library_id: string | null;
  session_snapshot: { blocks: SessionBlock[]; title: string; discipline: Discipline };
  scheduled_date: string;          // YYYY-MM-DD
  original_date: string | null;
  moved_by_athlete: boolean;
  completed: boolean;
  completed_at: string | null;
  // Retour athlète (séance globale)
  session_rpe: number | null;
  fatigue: number | null;          // 1-10
  pain: number | null;             // 0-10
  comment: string | null;
  created_at: string;
}

// ─────────────────────────────────────────────────────────────
// TESTS LACTATE
// ─────────────────────────────────────────────────────────────
export interface LactateTestStep {
  id: string;
  test_id: string;
  step_order: number;
  speed_kmh: number;
  lactate_mmol: number;
  hr_bpm: number | null;
  rpe: number | null;
}

export interface LactateTest {
  id: string;
  athlete_id: string;
  coach_id: string;
  test_date: string;
  rest_lactate_mmol: number | null;
  rest_hr_bpm: number | null;
  fc_max: number | null;
  steps: LactateTestStep[];
  // Résultats détectés
  lt1_ms: number | null;
  lt2_ms: number | null;
  created_at: string;
}

/** Retour de detectLT(steps). */
export interface LTDetectionResult {
  lt1: { speed_kmh: number; lactate_mmol: number } | null;
  lt2: { speed_kmh: number; lactate_mmol: number } | null;
  baseline: number;                // lactate de repos
  lt1Threshold: number;            // baseline + 0.5
}

// ─────────────────────────────────────────────────────────────
// PRÉPARATION PHYSIQUE
// ─────────────────────────────────────────────────────────────
export interface PpExercise {
  id: string;
  coach_id: string | null;
  club_id: string | null;
  name: string;
  category: ExerciseCategory;
  muscle_groups: MuscleGroup[];
  equipment: EquipmentType[];
  level: AthleteLevel;
  objective: string | null;
  video_url: string | null;
  image_url: string | null;
  technical_notes: string | null;
  instructions: string | null;
  contraindications: string | null;
  progression_id: string | null;
  regression_id: string | null;
  is_favorite: boolean;
  is_archived: boolean;
  use_count: number;
  created_at: string;
  updated_at: string;
}

/** Config spécifique selon set_type (union discriminée). */
export type SetConfig =
  | { kind: 'standard' }
  | { kind: 'drop_set'; drops: { pct_drop: number; reps: number | 'AMRAP' }[] }
  | { kind: 'super_set'; paired_exercise_ids: string[]; rest_between_pairs_sec: number; label: string }
  | { kind: 'myo_reps'; activation_reps: number; mini_sets: number; mini_reps: number; micro_rest_sec: number }
  | { kind: 'cluster'; cluster_reps: number[]; intra_rest_sec: number }
  | { kind: 'iso_overcoming'; push_sec: number; intent: 'max' | 'progressive'; reps: number; rest_sec: number; joint_angle_deg: number }
  | { kind: 'iso_yielding'; hold_sec: number; load_pct_rm: number; joint_angle_deg: number };

export interface StrengthSessionExercise {
  id: string;
  block_id: string;
  exercise_id: string;
  exercise_order: number;
  emom_minute: number | null;
  set_type: SetType;
  set_config: SetConfig | null;
  // Volume
  sets: number | null;
  reps_fixed: number | null;
  reps_min: number | null;
  reps_max: number | null;
  duration_sec: number | null;     // iso
  plyo_contacts: number | null;
  // Charge
  charge_ref_type: ChargeRefType | null;
  charge_kg_min: number | null;
  charge_kg_max: number | null;
  charge_pct_rm_min: number | null;
  charge_pct_rm_max: number | null;
  // Effort
  effort_ref_type: EffortRefType | null;
  rpe_min: number | null;
  rpe_max: number | null;
  rir_min: number | null;
  rir_max: number | null;
  // Tempo & repos
  tempo: string | null;            // ex "3-1-X-1"
  rest_sec_min: number | null;
  rest_sec_max: number | null;
  // Réathlé
  max_pain_allowed: number | null;
  coach_notes: string | null;
}

/** Champs que l'athlète doit renseigner (config par séance). */
export interface RequiredFields {
  charge: boolean;
  reps: boolean;
  rpe: boolean;
  rir: boolean;
  pain: boolean;
  comment: boolean;
}

export interface ScheduledStrengthSession {
  id: string;
  athlete_id: string;
  coach_id: string;
  strength_session_id: string | null;
  session_snapshot: unknown;       // copie figée de la séance + blocs + exercices
  scheduled_date: string;
  original_date: string | null;
  moved_by_athlete: boolean;
  cycle_id: string | null;
  required_fields: RequiredFields;
  completed: boolean;
  completed_at: string | null;
  session_rpe: number | null;
  session_fatigue: number | null;  // 1-10
  session_pain: number | null;     // 0-10
  session_comment: string | null;
  created_at: string;
}

export interface StrengthResult {
  id: string;
  scheduled_session_id: string;
  session_exercise_id: string | null;
  athlete_id: string;
  exercise_id: string;
  set_number: number;
  charge_kg: number | null;
  reps_done: number | null;
  duration_sec_done: number | null;
  rpe_done: number | null;
  rir_done: number | null;
  pain: number | null;             // 0-10
  comment: string | null;
  created_at: string;
}

export interface Athlete1RMProfile {
  id: string;
  athlete_id: string;
  exercise_id: string;
  rm_kg: number;
  source: 'estimated' | 'tested' | 'manual';
  updated_at: string;
}

export interface StrengthTest {
  id: string;
  athlete_id: string;
  coach_id: string | null;
  exercise_id: string;
  protocol: RmTestProtocol;
  test_date: string;
  charge_kg: number | null;
  reps_done: number | null;
  rpe: number | null;
  formula_used: RmFormula | null;
  result_1rm_kg: number;
  notes: string | null;
  applied_to_profile: boolean;
  created_at: string;
}

export interface StrengthLoadTracking {
  id: string;
  athlete_id: string;
  scheduled_session_id: string | null;
  date: string;
  mechanical_load_au: number;      // Σ(charge×reps×séries×%1RM)/100
  metabolic_load_au: number;       // RPE séance × durée min
  ratio_mech_metab: number;
  session_count: number;
  created_at: string;
}

// ─────────────────────────────────────────────────────────────
// CHARGE GLOBALE (ACWR unifié course + force)
// ─────────────────────────────────────────────────────────────
export interface TrainingLoadCache {
  id: string;
  athlete_id: string;
  date: string;
  acute_load_7d: number | null;    // ATL
  chronic_load_28d: number | null; // CTL
  ratio: number | null;            // ACWR
  domain1_pct_7d: number | null;
  domain2_pct_7d: number | null;
  domain3_pct_7d: number | null;
  updated_at: string;
}
