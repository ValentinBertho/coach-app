export type ExerciseCategory =
  | 'FORCE_MAX' | 'HYPERTROPHIE' | 'PUISSANCE' | 'PLIOMETRIE' | 'ISOMETRIE'
  | 'ENDURANCE_MUSCULAIRE' | 'GAINAGE' | 'MOBILITE' | 'REATHLETISATION' | 'PREVENTION';

export type MuscleGroup =
  | 'QUADRICEPS' | 'ISCHIOS' | 'MOLLETS' | 'FESSIERS' | 'TRONC' | 'HAUT_DU_CORPS'
  | 'PIED_CHEVILLE' | 'HANCHE' | 'DOS' | 'EPAULE';

export type EquipmentType =
  | 'POIDS_DU_CORPS' | 'HALTERES' | 'KETTLEBELL' | 'BARRE' | 'MACHINE' | 'ELASTIQUE'
  | 'MEDECINE_BALL' | 'BOX' | 'TRX' | 'POULIE' | 'LEG_EXTENSION' | 'LEG_CURL' | 'PRESSE' | 'AUTRE';

export type ExerciseLevel = 'DEBUTANT' | 'INTERMEDIAIRE' | 'AVANCE';
export type RmFormula = 'EPLEY' | 'BRZYCKI' | 'RIR_BASED' | 'NUZZO';
export type ChargeRefType = 'KG_FIXE' | 'KG_RANGE' | 'PCT_RM' | 'PCT_RM_RANGE' | 'RM_CIBLE' | 'RM_ESTIME';
export type EffortRefType = 'RPE' | 'RPE_RANGE' | 'RIR' | 'RIR_RANGE';
export type SetType =
  | 'STANDARD' | 'DROP_SET' | 'SUPER_SET' | 'MYO_REPS' | 'CLUSTER' | 'ISO_OVERCOMING' | 'ISO_YIELDING';
export type BlockType = 'ECHAUFFEMENT' | 'ACTIVATION' | 'PRINCIPAL' | 'ACCESSOIRE' | 'CALME';
export type BlockFormat =
  | 'CLASSIQUE' | 'EMOM' | 'AMRAP' | 'FOR_TIME' | 'CIRCUIT' | 'ISOMETRIE' | 'PLIOMETRIE';

export interface PpExercise {
  id: string;
  name: string;
  category: ExerciseCategory;
  /** Catégorie de l'arbre unifié (domaine STRENGTH), optionnelle (QA1). */
  categoryId: string | null;
  level: ExerciseLevel | null;
  objective: string | null;
  muscleGroups: MuscleGroup[];
  equipment: EquipmentType[];
  videoUrl: string | null;
  imageUrl: string | null;
  instructions: string | null;
  technicalNotes: string | null;
  contraindications: string | null;
  progressionId: string | null;
  regressionId: string | null;
  favorite: boolean;
  useCount: number;
}

export interface PpExerciseRequest {
  name: string;
  category: ExerciseCategory;
  categoryId?: string | null;
  level?: ExerciseLevel | null;
  objective?: string | null;
  muscleGroups?: MuscleGroup[];
  equipment?: EquipmentType[];
  videoUrl?: string | null;
  imageUrl?: string | null;
  instructions?: string | null;
  technicalNotes?: string | null;
  contraindications?: string | null;
  progressionId?: string | null;
  regressionId?: string | null;
}

export interface WorkZone {
  name: string;
  pctMin: number;
  pctMax: number;
  kgMin: number;
  kgMax: number;
}

export interface E1rmResult {
  e1rm: number;
  formula: RmFormula;
  zones: WorkZone[];
}

export interface StrengthPrescription {
  chargeRefType?: ChargeRefType;
  chargeKgMin?: number | null;
  chargeKgMax?: number | null;
  chargePctRmMin?: number | null;
  chargePctRmMax?: number | null;
  effortRefType?: EffortRefType;
  rpeMin?: number | null;
  rpeMax?: number | null;
  rirMin?: number | null;
  rirMax?: number | null;
  sets?: number | null;
  repsFixed?: number | null;
  repsMin?: number | null;
  repsMax?: number | null;
  durationSec?: number | null;
  tempo?: string | null;
  restSecMin?: number | null;
  restSecMax?: number | null;
}

export interface StrengthExerciseItem {
  exerciseId: string;
  exerciseName: string;
  setType: SetType;
  prescription: StrengthPrescription;
  setConfig?: unknown;
  coachNotes?: string | null;
}

export interface StrengthBlock {
  id: string;
  blockType: BlockType;
  format: BlockFormat;
  durationSec?: number | null;
  rounds?: number | null;
  workSec?: number | null;
  restSec?: number | null;
  exercises: StrengthExerciseItem[];
}

export interface StrengthStructure {
  blocks: StrengthBlock[];
}

export interface StrengthSession {
  id: string;
  name: string;
  notes: string | null;
  favorite: boolean;
  archived: boolean;
  useCount: number;
  structure: StrengthStructure;
}

export interface Athlete1rm {
  exerciseId: string;
  /** Nom porté par le DTO : la bibliothèque d'exercices est paginée côté écran. */
  exerciseName: string;
  rmKg: number;
  source: string;
}

/** 1RM courant enrichi du nom d'exercice (portail athlète /me). */
export interface MyOneRm {
  exerciseId: string;
  exerciseName: string;
  rmKg: number;
  source: string;
}

export interface E1rmHistory {
  exerciseId: string;
  e1rmKg: number;
  chargeKg: number;
  reps: number;
  rpeOrRir: string | null;
  calculatedAt: string;
}

export interface ChargeTarget {
  computable: boolean;
  oneRmKg: number | null;
  kgMin: number | null;
  kgMax: number | null;
  chargeLabel: string | null;
  effortLabel: string | null;
}

export interface CalculatedStrength {
  blocks: { block: StrengthBlock; exercises: { item: StrengthExerciseItem; charge: ChargeTarget }[] }[];
}

/**
 * Prescription figée d'une séance de force planifiée : structure snapshot au moment de
 * l'assignation, charges calculées pour l'athlète, et champs demandés au retour.
 * Servie à l'identique côté coach (calendrier) et côté athlète (portail).
 */
export interface StrengthPrescriptionView {
  snapshot: StrengthStructure;
  calculated: CalculatedStrength | null;
  requiredFields: Record<string, boolean> | null;
}

export interface ScheduledStrength {
  id: string;
  athleteId: string;
  sourceSessionId: string | null;
  title: string;
  scheduledDate: string;
  originalDate: string | null;
  movedByAthlete: boolean;
  completed: boolean;
  sessionFatigue: number | null;
  sessionPain: number | null;
  /** Résumé des charges calculées, renseigné à la planification (CdC §8). */
  chargeSummary?: string | null;
  /** Date du « vu 👏 » du coach sur le débrief ; null tant qu'il n'a pas eu lieu. */
  coachAcknowledgedAt?: string | null;
}

export interface CycleWeek {
  week: number;
  sessionIds: string[];
  chargePctAdjustment: number;
}

export interface CycleStructure {
  weeks: CycleWeek[];
}

export interface StrengthCycle {
  id: string;
  name: string;
  weeks: number;
  objective: string | null;
  description: string | null;
  structure: CycleStructure;
}

export interface StrengthLoadPoint {
  scheduledSessionId: string | null;
  sessionDate: string;
  mechanicalLoad: number;
  metabolicLoad: number;
}

export interface ExerciseProgression {
  exerciseId: string;
  exerciseName: string;
  recommended: boolean;
  suggestionLabel: string;
  deltaKg: number | null;
}

export interface ProgressionAlert {
  level: 'HIGH' | 'MEDIUM';
  code: string;
  message: string;
  exerciseId: string;
  exerciseName: string;
}

export interface Progression {
  scheduledId: string;
  exercises: ExerciseProgression[];
  alerts: ProgressionAlert[];
}

export type StrengthTestProtocol = 'TRUE_1RM' | 'REP_TEST_3_5' | 'AMRAP_TEST' | 'ISO_MVC';

/**
 * Libellés FR des protocoles de test. Partagés : l'écran de force du coach et le suivi de
 * l'athlète doivent nommer un test de la même façon — « AMRAP » ici et « Test AMRAP » là serait
 * deux noms pour la même mesure.
 */
export const STRENGTH_TEST_PROTOCOL_LABELS: Record<StrengthTestProtocol, string> = {
  TRUE_1RM: '1RM direct (1 rép. max)',
  REP_TEST_3_5: "Test 3–5 reps (à l'échec)",
  AMRAP_TEST: 'AMRAP (reps max à charge fixe)',
  ISO_MVC: 'Isométrie max (MVC)',
};

export interface StrengthTest {
  id: string;
  exerciseId: string;
  protocol: StrengthTestProtocol;
  testDate: string;
  weightKg: number | null;
  reps: number | null;
  durationSec: number | null;
  rir: number | null;
  computedE1rmKg: number;
  notes: string | null;
}

export interface StrengthTestRequest {
  exerciseId: string;
  protocol: StrengthTestProtocol;
  testDate?: string;
  weightKg?: number | null;
  reps?: number | null;
  durationSec?: number | null;
  rir?: number | null;
  notes?: string | null;
  /**
   * Confirme un écart de plus de 10 % avec le 1RM courant. Sans lui le serveur refuse en 409 :
   * un test mal placé faisait chuter le profil, et toutes les charges prescrites avec lui.
   */
  confirmLargeGap?: boolean;
}

export interface StrengthResultEntry {
  exerciseId: string;
  setNumber: number;
  chargeKg: number | null;
  repsDone: number | null;
  rirDone?: number | null;
  rpeDone?: number | null;
  pain?: number | null;
  comment?: string | null;
}
