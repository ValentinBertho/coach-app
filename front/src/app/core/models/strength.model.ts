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
  level?: ExerciseLevel | null;
  objective?: string | null;
  muscleGroups?: MuscleGroup[];
  equipment?: EquipmentType[];
  videoUrl?: string | null;
  instructions?: string | null;
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
}
