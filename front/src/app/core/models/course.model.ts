export type PrescriptionRef =
  | 'PCT_LT1' | 'PCT_LT2' | 'PCT_VC'
  | 'PCT_PACE_800M' | 'PCT_PACE_1500M' | 'PCT_PACE_3000M' | 'PCT_PACE_5KM'
  | 'PCT_PACE_10KM' | 'PCT_PACE_15KM' | 'PCT_PACE_SEMI' | 'PCT_PACE_MARATHON';

export interface CoursePrescription {
  ref: PrescriptionRef;
  minPct: number;
  maxPct: number;
}

export interface CourseRecovery {
  type: string;
  durationS?: number | null;
  distanceM?: number | null;
  prescription?: CoursePrescription | null;
}

export interface CourseBlock {
  id: string;
  type: string;
  reps?: number | null;
  distanceM?: number | null;
  durationS?: number | null;
  prescription?: CoursePrescription | null;
  recovery?: CourseRecovery | null;
  note?: string | null;
  /** Éducatifs (gammes) attachés au bloc (ids) — ex. échauffement. */
  drillIds?: string[] | null;
}

export interface SessionStructure {
  warmup: CourseBlock[];
  main: CourseBlock[];
  cooldown: CourseBlock[];
}

export interface CourseStructureResponse {
  templateId: string;
  name: string;
  discipline: string | null;
  categoryId: string | null;
  categoryName: string | null;
  favorite: boolean;
  archived: boolean;
  useCount: number;
  structure: SessionStructure;
}

export interface CalculatedBlock {
  computable: boolean;
  ref: PrescriptionRef;
  basePaceSecPerKm: number | null;
  paceMinSecPerKm: number | null;
  paceMaxSecPerKm: number | null;
  paceMinLabel: string | null;
  paceMaxLabel: string | null;
  speedMinKmh: number | null;
  speedMaxKmh: number | null;
  hrMin: number | null;
  hrMax: number | null;
  rpeMin: number | null;
  rpeMax: number | null;
  estimatedDurationS: number | null;
  estimatedDistanceM: number | null;
  /** Allure estimée depuis le VDOT (pas de seuil mesuré) — à signaler comme « estimée ». */
  paceEstimated?: boolean;
}

/** Un bloc de séance avec ses cibles calculées (et celles de sa récupération). */
export interface CalculatedBlockEntry {
  block: CourseBlock;
  calc: CalculatedBlock | null;
  recoveryCalc: CalculatedBlock | null;
}

/** Séance course entièrement calculée pour un athlète. */
export interface CalculatedSession {
  warmup: CalculatedBlockEntry[];
  main: CalculatedBlockEntry[];
  cooldown: CalculatedBlockEntry[];
  totalDistanceM: number | null;
  totalDurationS: number | null;
}

/** Éducatif (gamme) résolu pour l'affichage dans une séance. */
export interface CourseDrill {
  id: string;
  name: string;
  category: 'TECHNIQUE' | 'AMPLITUDE';
  description: string | null;
  videoUrl: string | null;
}

/** Prescription figée d'une séance planifiée : snapshot des blocs + cibles calculées. */
export interface WorkoutPrescription {
  snapshot: SessionStructure;
  calculated: CalculatedSession | null;
  /** Éducatifs référencés par les blocs, résolus (nom, vidéo). */
  drills?: CourseDrill[] | null;
}
